package net.astrorbits.football.client.match

import net.astrorbits.football.match.MatchPhase
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.network.HalfKickoffRequestC2SPayload
import net.astrorbits.football.network.MatchResultRequestC2SPayload
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object MatchStateClient {
    private var prevPhase: MatchPhase = MatchPhase.PRE_MATCH
    private var finishedTicks: Int = 0

    fun registerTickEvent() {
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
    }

    fun tick(client: Minecraft) {
        MatchStartClient.tickStoppage()
        val phase = MatchState.currentPhase
        // 进入新半场时终止未完成的开球计时（防止旧计时器在下一半场继续累积）
        if (phase != prevPhase) {
            if (phase == MatchPhase.FIRST_HALF || phase == MatchPhase.SECOND_HALF ||
                phase == MatchPhase.EXTRA_FIRST || phase == MatchPhase.EXTRA_SECOND
            ) {
                MatchStartClient.cancelKickoff()
                ClientPlayNetworking.send(HalfKickoffRequestC2SPayload.INSTANCE)
            }
            // 比赛结算：取消所有锁定
            if (phase == MatchPhase.FINISHED) {
                finishedTicks = 0
                MatchStartClient.reset()
                ClientPlayNetworking.send(MatchResultRequestC2SPayload.INSTANCE)
            }
            prevPhase = phase
        }
        // 结算后 16 秒自动回到开赛前
        if (phase == MatchPhase.FINISHED && client.level != null && !client.isPaused) {
            finishedTicks++
            if (finishedTicks >= 320) {
                MatchState.reset()
                finishedTicks = 0
            }
        }
        // 计时器由服务端权威驱动，客户端通过 MatchTimerSyncS2CPayload 同步
    }
}
