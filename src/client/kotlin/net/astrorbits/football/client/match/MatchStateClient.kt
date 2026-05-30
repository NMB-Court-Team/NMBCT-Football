package net.astrorbits.football.client.match

import net.astrorbits.football.match.MatchPhase
import net.astrorbits.football.match.MatchState
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

object MatchStateClient {
    fun registerTickEvent() {
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
    }

    fun tick(client: Minecraft) {
        if (MatchState.currentPhase == MatchPhase.PRE_MATCH || MatchState.currentPhase == MatchPhase.FINISHED) {
            return
        }
        if (MatchState.isRunning && client.level != null && !client.isPaused) {
            if (MatchState.isStoppagePhase()) {
                MatchState.stoppageTimerTicks++
            } else {
                MatchState.timerTicks++
            }
            val phase = MatchState.currentPhase
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