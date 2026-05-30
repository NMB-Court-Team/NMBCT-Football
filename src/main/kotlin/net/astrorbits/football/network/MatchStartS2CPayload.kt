package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.TeamSide
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 比赛开始 HUD 信息（队别、是否守门员、发球方、双方队名） */
data class MatchStartS2CPayload(
    val playerTeam: TeamSide,
    val isGk: Boolean,
    val kickoffTeam: TeamSide,
    val teamAName: String,
    val teamBName: String,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<MatchStartS2CPayload> = CustomPacketPayload.Type(NMBCTFootball.id("match_start"))
        val CODEC: StreamCodec<FriendlyByteBuf, MatchStartS2CPayload> = StreamCodec.composite(
            TeamSide.STREAM_CODEC, MatchStartS2CPayload::playerTeam,
            ByteBufCodecs.BOOL, MatchStartS2CPayload::isGk,
            TeamSide.STREAM_CODEC, MatchStartS2CPayload::kickoffTeam,
            ByteBufCodecs.STRING_UTF8, MatchStartS2CPayload::teamAName,
            ByteBufCodecs.STRING_UTF8, MatchStartS2CPayload::teamBName,
            ::MatchStartS2CPayload,
        )
    }
}
