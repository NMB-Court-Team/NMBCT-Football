package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.GoalLineOutType
import net.astrorbits.football.match.TeamSide
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 球出界，裁判判角球/球门球/抛边线球 */
data class GoalLineOutS2CPayload(
    val outType: GoalLineOutType,
    val restartTeam: TeamSide,
    val ballX: Double,
    val ballY: Double,
    val ballZ: Double,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<GoalLineOutS2CPayload> = CustomPacketPayload.Type(NMBCTFootball.id("goal_line_out"))
        val CODEC: StreamCodec<FriendlyByteBuf, GoalLineOutS2CPayload> = StreamCodec.composite(
            GoalLineOutType.STREAM_CODEC, GoalLineOutS2CPayload::outType,
            TeamSide.STREAM_CODEC, GoalLineOutS2CPayload::restartTeam,
            ByteBufCodecs.DOUBLE, GoalLineOutS2CPayload::ballX,
            ByteBufCodecs.DOUBLE, GoalLineOutS2CPayload::ballY,
            ByteBufCodecs.DOUBLE, GoalLineOutS2CPayload::ballZ,
            ::GoalLineOutS2CPayload,
        )
    }
}
