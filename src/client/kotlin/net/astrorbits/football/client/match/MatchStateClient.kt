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

    fun registerTickEvent() {
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
    }

    fun tick(client: Minecraft) {
        val phase = MatchState.currentPhase
        // 进入新半场时终止未完成的开球计时（防止旧计时器在下一半场继续累积）
        if (phase != prevPhase) {
            if (phase == MatchPhase.SECOND_HALF || phase == MatchPhase.EXTRA_FIRST || phase == MatchPhase.EXTRA_SECOND) {
                MatchStartClient.cancelKickoff()
                ClientPlayNetworking.send(HalfKickoffRequestC2SPayload.INSTANCE)
            }
            // 比赛结算：取消所有锁定
            if (phase == MatchPhase.FINISHED) {
                MatchStartClient.reset()
                ClientPlayNetworking.send(MatchResultRequestC2SPayload.INSTANCE)
            }
            prevPhase = phase
        }
        // 阶段与「未开始」由服务端在结算约 16s 后 reset + MatchResetS2CPayload / MatchTimerSync 同步
    }
}
