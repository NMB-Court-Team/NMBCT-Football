package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.GoalLineOutType
import net.astrorbits.football.match.TeamSide
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 球出底线未进球，裁判判角球或球门球 */
data class GoalLineOutS2CPayload(
    val outType: GoalLineOutType,
    val restartTeam: TeamSide,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<GoalLineOutS2CPayload> = CustomPacketPayload.Type(NMBCTFootball.id("goal_line_out"))
        val CODEC: StreamCodec<FriendlyByteBuf, GoalLineOutS2CPayload> = StreamCodec.composite(
            GoalLineOutType.STREAM_CODEC, GoalLineOutS2CPayload::outType,
            TeamSide.STREAM_CODEC, GoalLineOutS2CPayload::restartTeam,
            ::GoalLineOutS2CPayload,
        )
    }
}
