package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.TeamSide
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 单次点球开踢 Banner */
data class PenaltyKickStartS2CPayload(
    val kickerTeam: TeamSide,
    val kickerName: String,
    val penaltyScoreA: Int,
    val penaltyScoreB: Int,
    val kickNumber: Int,
    val suddenDeath: Boolean,
    val teamAName: String,
    val teamBName: String,
    /** true = 本轮点球已进，显示进球 Banner；false = 开踢提示。 */
    val scored: Boolean = false,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<PenaltyKickStartS2CPayload> =
            CustomPacketPayload.Type(NMBCTFootball.id("penalty_kick_start"))
        val CODEC: StreamCodec<FriendlyByteBuf, PenaltyKickStartS2CPayload> = StreamCodec.composite(
            TeamSide.STREAM_CODEC, PenaltyKickStartS2CPayload::kickerTeam,
            ByteBufCodecs.STRING_UTF8, PenaltyKickStartS2CPayload::kickerName,
            ByteBufCodecs.INT, PenaltyKickStartS2CPayload::penaltyScoreA,
            ByteBufCodecs.INT, PenaltyKickStartS2CPayload::penaltyScoreB,
            ByteBufCodecs.INT, PenaltyKickStartS2CPayload::kickNumber,
            ByteBufCodecs.BOOL, PenaltyKickStartS2CPayload::suddenDeath,
            ByteBufCodecs.STRING_UTF8, PenaltyKickStartS2CPayload::teamAName,
            ByteBufCodecs.STRING_UTF8, PenaltyKickStartS2CPayload::teamBName,
            ByteBufCodecs.BOOL, PenaltyKickStartS2CPayload::scored,
            ::PenaltyKickStartS2CPayload,
        )
    }
}
