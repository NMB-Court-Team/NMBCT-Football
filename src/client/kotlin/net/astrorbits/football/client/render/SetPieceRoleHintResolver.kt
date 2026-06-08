package net.astrorbits.football.client.render

import net.astrorbits.football.client.FootballOperabilityClient
import net.astrorbits.football.client.SetPieceClient
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.match.GoalKickPhase
import net.astrorbits.football.match.SetPieceKind
import net.astrorbits.football.match.TeamSide
import net.minecraft.client.player.LocalPlayer

/** 开球锁定期内，按球员身份解析屏幕底部角色提示（与 [KickoffLockHudElement] 同位置）。 */
object SetPieceRoleHintResolver {
    private const val COLOR_WAIT = 0xFFFFAA00.toInt()
    private const val COLOR_SERVE = 0xFF55FF55.toInt()

    data class Hint(val translationKey: String, val color: Int)

    fun isAwaitingSetPieceTouch(): Boolean {
        if (MatchStartClient.isChoosing) return false
        if (MatchStartClient.ballResetPending) return true
        return MatchStartClient.startTimeMs > 0L && !MatchStartClient.kickoffTouched
    }

    fun resolve(player: LocalPlayer): Hint? {
        if (!isAwaitingSetPieceTouch()) return null

        val restartTeam = activeRestartTeam() ?: return null
        val localTeam = FootballOperabilityClient.resolveLocalPlayerTeam(player) ?: MatchStartClient.playerTeam
        if (localTeam != restartTeam) {
            return Hint(KEY_WAIT_OPPONENT, COLOR_WAIT)
        }
        if (isSetPieceServer(player, restartTeam)) {
            return Hint(resolveServeKey(), COLOR_SERVE)
        }
        return Hint(KEY_WAIT_TEAMMATE, COLOR_WAIT)
    }

    private fun activeRestartTeam() =
        SetPieceClient.restartTeam ?: MatchStartClient.pendingRestartTeam ?: MatchStartClient.kickoffTeam

    private fun activeSetPieceKind(): SetPieceKind = when {
        SetPieceClient.kind != SetPieceKind.NONE -> SetPieceClient.kind
        MatchStartClient.pendingSetPieceKind != null -> MatchStartClient.pendingSetPieceKind!!
        else -> SetPieceKind.NONE
    }

    private fun isSetPieceServer(player: LocalPlayer, restartTeam: TeamSide): Boolean {
        if (MatchStartClient.ballResetPending) {
            return MatchStartClient.playerTeam == restartTeam
        }
        return when (SetPieceClient.kind) {
            SetPieceKind.FREE_KICK -> player.uuid == SetPieceClient.freeKickTakerUuid
            SetPieceKind.CORNER_KICK -> player.uuid == SetPieceClient.cornerKickTakerUuid
            SetPieceKind.THROW_IN -> player.uuid == SetPieceClient.throwInTakerUuid
            SetPieceKind.PENALTY_KICK -> player.uuid == SetPieceClient.penaltyKickerUuid
            SetPieceKind.GOAL_KICK -> when (SetPieceClient.goalKickPhase) {
                GoalKickPhase.WAITING_PICKUP, GoalKickPhase.PLACED -> true
                GoalKickPhase.PLACING -> player.uuid == SetPieceClient.goalKickPickerUuid
                else -> false
            }
            SetPieceKind.CENTER_KICKOFF, SetPieceKind.NONE -> MatchStartClient.isKickoffTeam
            else -> false
        }
    }

    private fun resolveServeKey(): String = when (activeSetPieceKind()) {
        SetPieceKind.FREE_KICK -> KEY_SERVE_FREE_KICK
        SetPieceKind.CORNER_KICK -> KEY_SERVE_CORNER_KICK
        SetPieceKind.THROW_IN -> KEY_SERVE_THROW_IN
        SetPieceKind.PENALTY_KICK -> KEY_SERVE_PENALTY
        SetPieceKind.GOAL_KICK -> when {
            MatchStartClient.ballResetPending ||
                SetPieceClient.goalKickPhase == GoalKickPhase.WAITING_PICKUP ||
                SetPieceClient.goalKickPhase == null -> KEY_SERVE_GOAL_KICK_PICKUP
            SetPieceClient.goalKickPhase == GoalKickPhase.PLACING -> KEY_SERVE_GOAL_KICK_PLACE
            else -> KEY_SERVE_GOAL_KICK
        }
        SetPieceKind.CENTER_KICKOFF, SetPieceKind.NONE -> KEY_SERVE_KICKOFF
        else -> KEY_SERVE_KICKOFF
    }

    private const val KEY_WAIT_OPPONENT = "hud.nmbct-football.set_piece.role.wait_opponent"
    private const val KEY_WAIT_TEAMMATE = "hud.nmbct-football.set_piece.role.wait_teammate"
    private const val KEY_SERVE_KICKOFF = "hud.nmbct-football.set_piece.role.serve.kickoff"
    private const val KEY_SERVE_FREE_KICK = "hud.nmbct-football.set_piece.role.serve.free_kick"
    private const val KEY_SERVE_CORNER_KICK = "hud.nmbct-football.set_piece.role.serve.corner_kick"
    private const val KEY_SERVE_THROW_IN = "hud.nmbct-football.set_piece.role.serve.throw_in"
    private const val KEY_SERVE_PENALTY = "hud.nmbct-football.set_piece.role.serve.penalty"
    private const val KEY_SERVE_GOAL_KICK = "hud.nmbct-football.set_piece.role.serve.goal_kick"
    private const val KEY_SERVE_GOAL_KICK_PICKUP = "hud.nmbct-football.set_piece.role.serve.goal_kick_pickup"
    private const val KEY_SERVE_GOAL_KICK_PLACE = "hud.nmbct-football.set_piece.role.serve.goal_kick_place"
}
