package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.MatchPhase
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 服务端定时广播计时器、阶段、比分、队名、关键比赛配置。 */
data class MatchTimerSyncS2CPayload(
    val timerTicks: Int,
    val stoppageTimerTicks: Int,
    val currentPhase: MatchPhase,
    val teamAScore: Int,
    val teamBScore: Int,
    val teamAName: String,
    val teamBName: String,
    val isRunning: Boolean,
    val halfTimeMinutes: Int,
    val stoppageTimeMaxMinutes: Int,
    val extraTimeHalfMinutes: Int,
    val enableStoppageTime: Boolean,
    val enableExtraTime: Boolean,
    val enablePenaltyShootout: Boolean,
    val dynamicStoppageTicks: Int,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<MatchTimerSyncS2CPayload> = CustomPacketPayload.Type(NMBCTFootball.id("match_timer_sync"))
        val CODEC: StreamCodec<FriendlyByteBuf, MatchTimerSyncS2CPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeInt(p.timerTicks)
                buf.writeInt(p.stoppageTimerTicks)
                MatchPhase.STREAM_CODEC.encode(buf, p.currentPhase)
                buf.writeInt(p.teamAScore)
                buf.writeInt(p.teamBScore)
                ByteBufCodecs.STRING_UTF8.encode(buf, p.teamAName)
                ByteBufCodecs.STRING_UTF8.encode(buf, p.teamBName)
                buf.writeBoolean(p.isRunning)
                buf.writeInt(p.halfTimeMinutes)
                buf.writeInt(p.stoppageTimeMaxMinutes)
                buf.writeInt(p.extraTimeHalfMinutes)
                buf.writeBoolean(p.enableStoppageTime)
                buf.writeBoolean(p.enableExtraTime)
                buf.writeBoolean(p.enablePenaltyShootout)
                buf.writeInt(p.dynamicStoppageTicks)
            },
            { buf ->
                MatchTimerSyncS2CPayload(
                    timerTicks = buf.readInt(),
                    stoppageTimerTicks = buf.readInt(),
                    currentPhase = MatchPhase.STREAM_CODEC.decode(buf),
                    teamAScore = buf.readInt(),
                    teamBScore = buf.readInt(),
                    teamAName = ByteBufCodecs.STRING_UTF8.decode(buf),
                    teamBName = ByteBufCodecs.STRING_UTF8.decode(buf),
                    isRunning = buf.readBoolean(),
                    halfTimeMinutes = buf.readInt(),
                    stoppageTimeMaxMinutes = buf.readInt(),
                    extraTimeHalfMinutes = buf.readInt(),
                    enableStoppageTime = buf.readBoolean(),
                    enableExtraTime = buf.readBoolean(),
                    enablePenaltyShootout = buf.readBoolean(),
                    dynamicStoppageTicks = buf.readInt(),
                )
            },
        )
    }
}
