package net.astrorbits.football.match

import net.minecraft.server.level.ServerPlayer

/** 定位球重发原因（用于 HUD 与 S2C 广播）。 */
data class SetPieceRestartCause(
    val reasonKey: String,
    val foulingPlayerName: String = "",
    val foulingTeam: TeamSide? = null,
) {
    companion object {
        val NONE = SetPieceRestartCause("")

        fun fromPlayer(player: ServerPlayer, reasonKey: String): SetPieceRestartCause =
            SetPieceRestartCause(
                reasonKey = reasonKey,
                foulingPlayerName = player.gameProfile.name,
                foulingTeam = MatchState.getPlayerTeam(player.uuid),
            )

        fun fromViolation(player: ServerPlayer, type: SetPieceAreaViolationType): SetPieceRestartCause {
            val key = type.restartReasonKey ?: return NONE
            return fromPlayer(player, key)
        }

        fun ballOnly(reasonKey: String) = SetPieceRestartCause(reasonKey = reasonKey)
    }
}

object SetPieceRestartReasonKeys {
    const val GOAL_KICK_EARLY_TOUCH = "hud.nmbct-football.restart.reason.goal_kick_early_touch"
    const val GOAL_KICK_PREMATURE_TOUCH = "hud.nmbct-football.restart.reason.goal_kick_premature_touch"
    const val GOAL_KICK_BALL_STATIONARY = "hud.nmbct-football.restart.reason.goal_kick_ball_stationary"
    const val FREE_KICK_BALL_IN_AREA = "hud.nmbct-football.restart.reason.free_kick_ball_in_area"
}
