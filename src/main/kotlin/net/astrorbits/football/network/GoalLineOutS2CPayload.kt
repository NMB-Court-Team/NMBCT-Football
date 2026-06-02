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
    val isKickoffTeam: Boolean,
    val ballX: Double,
    val ballY: Double,
    val ballZ: Double,
    val lastTouchPlayerName: String,
    /** -1 = 未知；0 = A；1 = B */
    val lastTouchTeamCode: Int,
) : CustomPacketPayload {
    override fun type() = TYPE

    val lastTouchTeam: TeamSide?
        get() = when (lastTouchTeamCode) {
            0 -> TeamSide.A
            1 -> TeamSide.B
            else -> null
        }

    companion object {
        val TYPE: CustomPacketPayload.Type<GoalLineOutS2CPayload> = CustomPacketPayload.Type(NMBCTFootball.id("goal_line_out"))
        val CODEC: StreamCodec<FriendlyByteBuf, GoalLineOutS2CPayload> = StreamCodec.composite(
            GoalLineOutType.STREAM_CODEC, GoalLineOutS2CPayload::outType,
            TeamSide.STREAM_CODEC, GoalLineOutS2CPayload::restartTeam,
            ByteBufCodecs.BOOL, GoalLineOutS2CPayload::isKickoffTeam,
            ByteBufCodecs.DOUBLE, GoalLineOutS2CPayload::ballX,
            ByteBufCodecs.DOUBLE, GoalLineOutS2CPayload::ballY,
            ByteBufCodecs.DOUBLE, GoalLineOutS2CPayload::ballZ,
            ByteBufCodecs.STRING_UTF8, GoalLineOutS2CPayload::lastTouchPlayerName,
            ByteBufCodecs.INT, GoalLineOutS2CPayload::lastTouchTeamCode,
            ::GoalLineOutS2CPayload,
        )

        fun encodeTouchTeam(team: TeamSide?): Int = when (team) {
            TeamSide.A -> 0
            TeamSide.B -> 1
            null -> -1
        }
    }
}
