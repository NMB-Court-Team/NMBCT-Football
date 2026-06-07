package net.astrorbits.football.match

import net.astrorbits.football.network.FootballActionType
import net.astrorbits.football.util.GoalkeeperUtil
import net.minecraft.server.level.ServerPlayer

private val FREE_KICK_DEFENDING_GK_HOLD_ACTIONS = setOf(
    FootballActionType.GK_THROW_SHORT,
    FootballActionType.GK_THROW_LONG,
    FootballActionType.GK_DROP,
)

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
            SetPieceKind.CORNER_KICK -> isCornerKickBlocked(player, team, ctx, action)
            SetPieceKind.FREE_KICK -> isFreeKickBlocked(player, team, ctx, action)
            SetPieceKind.CENTER_KICKOFF -> isCenterKickoffBlocked(player, team, ctx, action)
            else -> false
        }
    }

    fun allowsGoalKickCatch(player: ServerPlayer): Boolean {
        val ctx = SetPieceState.active ?: return false
        if (ctx.kind != SetPieceKind.GOAL_KICK) return false
        val team = MatchState.getPlayerTeam(player.uuid) ?: return false
        if (team != ctx.restartTeam) return false
        val phase = ctx.goalKickPhase ?: return false
        return (phase == GoalKickPhase.WAITING_PICKUP && PlayerRoleState.isDesignatedGoalkeeper(player)) ||
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

    /** 对方任意球期间，防守方门将已持球（扑救/摘球后需手抛或放下）。 */
    fun isFreeKickDefendingGoalkeeperHolding(player: ServerPlayer): Boolean {
        if (!isFreeKickDefendingGoalkeeper(player)) return false
        return GoalkeeperUtil.findHeldFootball(player) != null
    }

    fun allowsFreeKickDefendingGoalkeeperHoldAction(player: ServerPlayer, action: FootballActionType?): Boolean {
        if (!isFreeKickDefendingGoalkeeperHolding(player)) return false
        return action == null || action in FREE_KICK_DEFENDING_GK_HOLD_ACTIONS
    }

    private fun isFreeKickDefendingGoalkeeper(player: ServerPlayer): Boolean {
        val ctx = SetPieceState.active ?: return false
        if (ctx.kind != SetPieceKind.FREE_KICK) return false
        if (!PlayerRoleState.isGoalkeeper(player)) return false
        val team = MatchState.getPlayerTeam(player.uuid) ?: return false
        return team != ctx.restartTeam
    }

    /** 开球锁定期内，发球方仅允许传球开球（界外球除外）。 */
    fun isPassOnlyViolation(player: ServerPlayer, action: FootballActionType?): Boolean {
        if (action == null) return false
        if (action == FootballActionType.PASS) return false
        if (MatchState.kickoffTouched) return false
        val ctx = SetPieceState.active
        val team = MatchState.getPlayerTeam(player.uuid) ?: return false
        val kickoffTeam = MatchState.kickoffTeam ?: ctx?.restartTeam ?: return false
        if (team != kickoffTeam) return false

        if (ctx != null) {
            when (ctx.kind) {
                SetPieceKind.THROW_IN -> return false
                SetPieceKind.GOAL_KICK -> {
                    if (ctx.goalKickPhase == GoalKickPhase.PLACED &&
                        player.uuid == ctx.goalKickPickerUuid
                    ) {
                        return isKickOpenBlocked(action)
                    }
                    return false
                }
                SetPieceKind.CORNER_KICK -> {
                    if (player.uuid == ctx.cornerKickTakerUuid) return isKickOpenBlocked(action)
                    return false
                }
                SetPieceKind.FREE_KICK -> {
                    if (player.uuid == ctx.freeKickTakerUuid) return isKickOpenBlocked(action)
                    return false
                }
                SetPieceKind.CENTER_KICKOFF -> return isKickOpenBlocked(action)
                else -> Unit
            }
        }
        if (ctx == null && kickoffTeam == team) return isKickOpenBlocked(action)
        return false
    }

    /** 定位球开球：轻触 PASS、蓄力 SHOOT；禁止带球/挑球等。 */
    private fun isKickOpenBlocked(action: FootballActionType?): Boolean =
        action != null && action != FootballActionType.PASS && action != FootballActionType.SHOOT

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
                    if (action == FootballActionType.GK_CATCH && PlayerRoleState.isDesignatedGoalkeeper(player)) {
                        return false
                    }
                    return true
                }
                if (team == opponent) return true
            }
            GoalKickPhase.PLACING -> {
                if (player.uuid == ctx.goalKickPickerUuid) {
                    return action != null && action != FootballActionType.GK_DROP
                }
                return true
            }
            GoalKickPhase.PLACED -> {
                if (player.uuid == ctx.goalKickPickerUuid) {
                    return isKickOpenBlocked(action)
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
            return when (action) {
                FootballActionType.GK_THROW_SHORT,
                FootballActionType.GK_THROW_LONG,
                -> false
                null -> false
                else -> true
            }
        }
        if (team != ctx.restartTeam) return false
        return true
    }

    private fun isCornerKickBlocked(
        player: ServerPlayer,
        team: TeamSide,
        ctx: SetPieceContext,
        action: FootballActionType?,
    ): Boolean {
        if (MatchState.kickoffTouched) return false
        if (player.uuid == ctx.cornerKickTakerUuid) {
            return isKickOpenBlocked(action)
        }
        if (team == ctx.restartTeam) return true
        return false
    }

    private fun isFreeKickBlocked(
        player: ServerPlayer,
        team: TeamSide,
        ctx: SetPieceContext,
        action: FootballActionType?,
    ): Boolean {
        if (MatchState.kickoffTouched) return false
        if (player.uuid == ctx.freeKickTakerUuid) {
            return isKickOpenBlocked(action)
        }
        if (team == ctx.restartTeam) return true
        return false
    }

    private fun isCenterKickoffBlocked(
        player: ServerPlayer,
        team: TeamSide,
        ctx: SetPieceContext,
        action: FootballActionType?,
    ): Boolean {
        if (MatchState.kickoffTouched) return false
        if (team == ctx.restartTeam) {
            return isKickOpenBlocked(action)
        }
        return false
    }

    fun isPlayerBallMovementForbidden(player: ServerPlayer): Boolean {
        if (ThrowInSetPieceFlow.isMovementFrozen(player)) return false
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
