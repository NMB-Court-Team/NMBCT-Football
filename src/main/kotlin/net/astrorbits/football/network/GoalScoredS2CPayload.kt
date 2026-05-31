package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.TeamSide
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 进球信息（得分方、进球者、进球者队伍、比分、是否乌龙） */
data class GoalScoredS2CPayload(
    val scoringTeam: TeamSide,
    val scorerName: String,
    val scorerTeam: TeamSide,
    val teamAScore: Int,
    val teamBScore: Int,
    val ownGoal: Boolean,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<GoalScoredS2CPayload> = CustomPacketPayload.Type(NMBCTFootball.id("goal_scored"))
        val CODEC: StreamCodec<FriendlyByteBuf, GoalScoredS2CPayload> = StreamCodec.composite(
            TeamSide.STREAM_CODEC, GoalScoredS2CPayload::scoringTeam,
            ByteBufCodecs.STRING_UTF8, GoalScoredS2CPayload::scorerName,
            TeamSide.STREAM_CODEC, GoalScoredS2CPayload::scorerTeam,
            ByteBufCodecs.INT, GoalScoredS2CPayload::teamAScore,
            ByteBufCodecs.INT, GoalScoredS2CPayload::teamBScore,
            ByteBufCodecs.BOOL, GoalScoredS2CPayload::ownGoal,
            ::GoalScoredS2CPayload,
        )
    }
}
