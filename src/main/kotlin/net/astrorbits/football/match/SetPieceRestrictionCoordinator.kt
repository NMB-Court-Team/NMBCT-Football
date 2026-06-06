package net.astrorbits.football.match

import net.astrorbits.football.network.FootballActionType
import net.astrorbits.football.util.GoalkeeperUtil
import net.minecraft.server.level.ServerPlayer

/**
 * 定位球期间硬性操作限制的统一裁决（服务端权威；客户端镜像见 [net.astrorbits.football.client.SetPieceClient]）。
 */
object SetPieceRestrictionCoordinator {
    fun isFootballOperationBlocked(player: ServerPlayer, action: FootballActionType? = null): Boolean {
        if (ThrowInSetPieceFlow.isMovementFrozen(player)) {
            if (ThrowInSetPieceFlow.allowsThrowAction(player, action)) return false
            return true
        }
        return isGeneralFootballBlocked(player, action)
    }

    fun isGeneralFootballBlocked(player: ServerPlayer, action: FootballActionType? = null): Boolean {
        val ctx = SetPieceState.active ?: return false
        val team = MatchState.getPlayerTeam(player.uuid) ?: return false

        return when (ctx.kind) {
            SetPieceKind.GOAL_KICK -> isGoalKickBlocked(player, team, ctx, action)
            SetPieceKind.THROW_IN -> isThrowInBlocked(player, team, ctx, action)
            else -> false
        }
    }

    fun allowsGoalKickCatch(player: ServerPlayer): Boolean {
        val ctx = SetPieceState.active ?: return false
        if (ctx.kind != SetPieceKind.GOAL_KICK) return false
        val team = MatchState.getPlayerTeam(player.uuid) ?: return false
        if (team != ctx.restartTeam) return false
        val phase = ctx.goalKickPhase ?: return false
        return phase == GoalKickPhase.WAITING_PICKUP ||
            (phase == GoalKickPhase.PLACED &&
                player.uuid == ctx.goalKickPickerUuid &&
                PlayerRoleState.isGoalkeeper(player))
    }

    fun allowsCatchDespiteRole(player: ServerPlayer): Boolean = allowsGoalKickCatch(player)

    fun allowsThrowInHold(player: ServerPlayer): Boolean = ThrowInSetPieceFlow.isMovementFrozen(player)

    fun allowsGoalKickDrop(player: ServerPlayer): Boolean {
        val ctx = SetPieceState.active ?: return false
        if (ctx.kind != SetPieceKind.GOAL_KICK) return false
        return ctx.goalKickPhase == GoalKickPhase.PLACING && player.uuid == ctx.goalKickPickerUuid
    }

    private fun isGoalKickBlocked(
        player: ServerPlayer,
        team: TeamSide,
        ctx: SetPieceContext,
        action: FootballActionType?,
    ): Boolean {
        val defending = ctx.defendingSide ?: return false
        val opponent = defending.opponent()

        if (action == FootballActionType.GK_CATCH && allowsGoalKickCatch(player)) {
            return false
        }

        when (ctx.goalKickPhase) {
            GoalKickPhase.WAITING_PICKUP -> {
                if (team == ctx.restartTeam) {
                    return action != FootballActionType.GK_CATCH
                }
                if (team == opponent) return true
            }
            GoalKickPhase.PLACING -> {
                if (player.uuid == ctx.goalKickPickerUuid) {
                    return action != FootballActionType.GK_DROP
                }
                return true
            }
            GoalKickPhase.PLACED -> {
                if (player.uuid == ctx.goalKickPickerUuid) {
                    return false
                }
                if (team == ctx.restartTeam) {
                    return true
                }
                if (team == opponent) return true
            }
            null -> Unit
        }
        return false
    }

    private fun isThrowInBlocked(
        player: ServerPlayer,
        team: TeamSide,
        ctx: SetPieceContext,
        action: FootballActionType?,
    ): Boolean {
        if (player.uuid == ctx.throwInTakerUuid) {
            return action != FootballActionType.GK_THROW_SHORT &&
                action != FootballActionType.GK_THROW_LONG
        }
        if (team != ctx.restartTeam) return false
        return true
    }

    fun isPlayerBallMovementForbidden(player: ServerPlayer): Boolean {
        if (isGeneralFootballBlocked(player)) return true
        val ctx = SetPieceState.active ?: return false
        if (ctx.kind == SetPieceKind.GOAL_KICK) {
            val team = MatchState.getPlayerTeam(player.uuid) ?: return false
            val defending = ctx.defendingSide ?: return false
            if (team == defending.opponent()) return true
        }
        return false
    }

    fun isGkHoldOutsidePenaltyAreaViolation(player: ServerPlayer): Boolean {
        if (!PlayerRoleState.isGoalkeeper(player)) return false
        if (GoalkeeperUtil.findHeldFootball(player) == null) return false
        val team = MatchState.getPlayerTeam(player.uuid) ?: return false
        return !MatchFieldAreaUtil.isPlayerInPenaltyArea(player, team)
    }
}
