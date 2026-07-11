package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.TeamSide
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 比赛结算（正赛比分、队名、是否平局；可选点球大战结果） */
data class MatchResultS2CPayload(
    val teamAScore: Int,
    val teamBScore: Int,
    val teamAName: String,
    val teamBName: String,
    val isDraw: Boolean,
    val wonByPenalties: Boolean = false,
    val penaltyScoreA: Int = 0,
    val penaltyScoreB: Int = 0,
    val penaltyWinner: TeamSide? = null,
    val forfeitWinner: TeamSide? = null,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<MatchResultS2CPayload> = CustomPacketPayload.Type(NMBCTFootball.id("match_result"))
        val CODEC: StreamCodec<FriendlyByteBuf, MatchResultS2CPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeInt(p.teamAScore)
                buf.writeInt(p.teamBScore)
                ByteBufCodecs.STRING_UTF8.encode(buf, p.teamAName)
                ByteBufCodecs.STRING_UTF8.encode(buf, p.teamBName)
                buf.writeBoolean(p.isDraw)
                buf.writeBoolean(p.wonByPenalties)
                buf.writeInt(p.penaltyScoreA)
                buf.writeInt(p.penaltyScoreB)
                buf.writeInt(p.penaltyWinner?.ordinal ?: -1)
                buf.writeInt(p.forfeitWinner?.ordinal ?: -1)
            },
            { buf ->
                MatchResultS2CPayload(
                    teamAScore = buf.readInt(),
                    teamBScore = buf.readInt(),
                    teamAName = ByteBufCodecs.STRING_UTF8.decode(buf),
                    teamBName = ByteBufCodecs.STRING_UTF8.decode(buf),
                    isDraw = buf.readBoolean(),
                    wonByPenalties = buf.readBoolean(),
                    penaltyScoreA = buf.readInt(),
                    penaltyScoreB = buf.readInt(),
                    penaltyWinner = buf.readInt().let { ord ->
                        if (ord < 0) null else TeamSide.entries[ord]
                    },
                    forfeitWinner = buf.readInt().let { ord ->
                        if (ord < 0) null else TeamSide.entries[ord]
                    },
                )
            },
        )
    }
}
