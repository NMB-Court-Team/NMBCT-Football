package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 比赛结算（比分、队名、是否平局） */
data class MatchResultS2CPayload(
    val teamAScore: Int,
    val teamBScore: Int,
    val teamAName: String,
    val teamBName: String,
    val isDraw: Boolean,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<MatchResultS2CPayload> = CustomPacketPayload.Type(NMBCTFootball.id("match_result"))
        val CODEC: StreamCodec<FriendlyByteBuf, MatchResultS2CPayload> = StreamCodec.composite(
            ByteBufCodecs.INT, MatchResultS2CPayload::teamAScore,
            ByteBufCodecs.INT, MatchResultS2CPayload::teamBScore,
            ByteBufCodecs.STRING_UTF8, MatchResultS2CPayload::teamAName,
            ByteBufCodecs.STRING_UTF8, MatchResultS2CPayload::teamBName,
            ByteBufCodecs.BOOL, MatchResultS2CPayload::isDraw,
            ::MatchResultS2CPayload,
        )
    }
}
