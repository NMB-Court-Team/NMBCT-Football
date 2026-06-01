package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.MatchPhase
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 服务端定时广播计时器与阶段，客户端 HUD 据此同步。 */
data class MatchTimerSyncS2CPayload(
    val timerTicks: Int,
    val stoppageTimerTicks: Int,
    val currentPhase: MatchPhase,
    val teamAScore: Int,
    val teamBScore: Int,
    val isRunning: Boolean,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<MatchTimerSyncS2CPayload> = CustomPacketPayload.Type(NMBCTFootball.id("match_timer_sync"))
        val CODEC: StreamCodec<FriendlyByteBuf, MatchTimerSyncS2CPayload> = StreamCodec.composite(
            ByteBufCodecs.INT, MatchTimerSyncS2CPayload::timerTicks,
            ByteBufCodecs.INT, MatchTimerSyncS2CPayload::stoppageTimerTicks,
            MatchPhase.STREAM_CODEC, MatchTimerSyncS2CPayload::currentPhase,
            ByteBufCodecs.INT, MatchTimerSyncS2CPayload::teamAScore,
            ByteBufCodecs.INT, MatchTimerSyncS2CPayload::teamBScore,
            ByteBufCodecs.BOOL, MatchTimerSyncS2CPayload::isRunning,
            ::MatchTimerSyncS2CPayload,
        )
    }
}
