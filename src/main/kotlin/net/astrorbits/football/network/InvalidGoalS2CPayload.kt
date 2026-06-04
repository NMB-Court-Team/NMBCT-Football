package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.TeamSide
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 无效进球（比分不变） */
data class InvalidGoalS2CPayload(
    val scorerName: String,
    val scorerTeam: TeamSide,
    val teamAScore: Int,
    val teamBScore: Int,
    val teamAName: String,
    val teamBName: String,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<InvalidGoalS2CPayload> =
            CustomPacketPayload.Type(NMBCTFootball.id("invalid_goal"))
        val CODEC: StreamCodec<FriendlyByteBuf, InvalidGoalS2CPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, InvalidGoalS2CPayload::scorerName,
            TeamSide.STREAM_CODEC, InvalidGoalS2CPayload::scorerTeam,
            ByteBufCodecs.INT, InvalidGoalS2CPayload::teamAScore,
            ByteBufCodecs.INT, InvalidGoalS2CPayload::teamBScore,
            ByteBufCodecs.STRING_UTF8, InvalidGoalS2CPayload::teamAName,
            ByteBufCodecs.STRING_UTF8, InvalidGoalS2CPayload::teamBName,
            ::InvalidGoalS2CPayload,
        )
    }
}
