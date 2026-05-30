package net.astrorbits.football.client.match

import net.astrorbits.football.match.MatchPhase
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.network.HalfKickoffRequestC2SPayload
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object MatchStateClient {
    private var prevPhase: MatchPhase = MatchPhase.PRE_MATCH

    fun registerTickEvent() {
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
    }

    fun tick(client: Minecraft) {
        MatchStartClient.tickStoppage()
        val phase = MatchState.currentPhase
        // 进入新半场时终止未完成的开球计时（防止旧计时器在下一半场继续累积）
        if (phase != prevPhase) {
            if (phase == MatchPhase.SECOND_HALF || phase == MatchPhase.EXTRA_FIRST || phase == MatchPhase.EXTRA_SECOND) {
                MatchStartClient.cancelKickoff()
                ClientPlayNetworking.send(HalfKickoffRequestC2SPayload.INSTANCE)
            }
            prevPhase = phase
        }
        if (phase == MatchPhase.PRE_MATCH || phase == MatchPhase.FINISHED) {
            return
        }
        if (MatchState.isRunning && client.level != null && !client.isPaused) {
            if (MatchState.isStoppagePhase()) {
                MatchState.stoppageTimerTicks++
            } else {
                MatchState.timerTicks++
            }
            if (phase == MatchPhase.PENALTIES) return
            val remaining = MatchState.getPhaseRemainingTicks()
            if (remaining <= 0) {
                val next = MatchState.getNextPhaseForAutoAdvance()
                if (next != null) {
                    MatchState.setPhase(next)
                }
            }
        }
    }
}
