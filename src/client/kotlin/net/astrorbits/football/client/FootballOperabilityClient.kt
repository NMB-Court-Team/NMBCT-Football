package net.astrorbits.football.client

import net.astrorbits.football.Football
import net.astrorbits.football.client.key.FootballInputHandler
import net.astrorbits.football.client.key.FootballKeyBindings
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.client.match.PenaltyShootoutClient
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.astrorbits.football.match.*
import net.minecraft.client.KeyMapping
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.level.Level

object FootballOperabilityClient {
    private const val SCOREBOARD_TEAM_A = "football_A"
    private const val SCOREBOARD_TEAM_B = "football_B"

    /** 比赛进行中仅守门员可用守门员场地操作；比赛未开始（含准备/结算）时所有球员可用。 */
    fun canUseGoalkeeperActions(): Boolean =
        GoalkeeperStateClient.isGoalkeeper || !MatchState.isDuringMatch()

    fun resolveLocalPlayerTeam(player: LocalPlayer): TeamSide? {
        val sbTeam = player.level().scoreboard.getPlayersTeam(player.gameProfile.name) ?: return null
        return when (sbTeam.name) {
            SCOREBOARD_TEAM_A -> TeamSide.A
            SCOREBOARD_TEAM_B -> TeamSide.B
            else -> null
        }
    }

    private fun localPlayerTeam(player: LocalPlayer): TeamSide =
        resolveLocalPlayerTeam(player) ?: MatchStartClient.playerTeam

    fun isPenaltyKicker(player: LocalPlayer): Boolean {
        val uuid = player.uuid
        if (MatchState.currentPhase == MatchPhase.PENALTIES && PenaltyShootoutClient.active) {
            return uuid == PenaltyShootoutClient.currentKickerUuid
        }
        if (SetPieceClient.kind == SetPieceKind.PENALTY_KICK) {
            return uuid == SetPieceClient.penaltyKickerUuid
        }
        return false
    }

    fun isPenaltyKickerAwaitingKick(player: LocalPlayer): Boolean {
        if (!isPenaltyKicker(player)) return false
        if (MatchState.currentPhase == MatchPhase.PENALTIES && PenaltyShootoutClient.active) {
            return PenaltyShootoutClient.kickPhase == PenaltyKickPhase.AWAITING_KICK
        }
        return SetPieceClient.penaltyKickPhase == PenaltyKickPhase.AWAITING_KICK
    }

    /** 定位球主罚待开球（轻触传球 / 蓄力射门）。 */
    fun isSetPieceKickTakerAwaitingKick(player: LocalPlayer): Boolean {
        if (MatchStartClient.kickoffTouched) return false
        if (SetPieceClient.restartTeam != MatchStartClient.playerTeam) return false
        return when (SetPieceClient.kind) {
            SetPieceKind.FREE_KICK -> player.uuid == SetPieceClient.freeKickTakerUuid
            SetPieceKind.CORNER_KICK -> player.uuid == SetPieceClient.cornerKickTakerUuid
            SetPieceKind.GOAL_KICK -> SetPieceClient.goalKickPhase == GoalKickPhase.PLACED
            else -> false
        }
    }

    private fun isPenaltyShootoutDefendingGoalkeeper(player: LocalPlayer): Boolean {
        if (MatchState.currentPhase != MatchPhase.PENALTIES || !PenaltyShootoutClient.active) {
            return false
        }
        if (!GoalkeeperStateClient.isGoalkeeper) {
            return false
        }
        return localPlayerTeam(player) == PenaltyShootoutClient.activeDefendingTeam
    }

    /** 按住右键鱼跃蓄力（含点球大战 Banner 期间）。 */
    fun canPrepareGoalkeeperDiveCharge(player: LocalPlayer): Boolean {
        if (!canUseGoalkeeperActions()) {
            return false
        }
        if (!MatchState.isDuringMatch()) {
            return true
        }
        if (isPenaltyShootoutDefendingGoalkeeper(player)) {
            return PenaltyShootoutClient.kickPhase == PenaltyKickPhase.SETUP ||
                PenaltyShootoutClient.kickPhase == PenaltyKickPhase.AWAITING_KICK
        }
        return canUseDiveAndCatch(player)
    }

    /** 释放右键执行鱼跃扑救。 */
    fun canExecuteGoalkeeperDive(player: LocalPlayer): Boolean {
        if (!canUseGoalkeeperActions()) {
            return false
        }
        if (!MatchState.isDuringMatch()) {
            return true
        }
        if (isPenaltyShootoutDefendingGoalkeeper(player)) {
            return PenaltyShootoutClient.kickPhase == PenaltyKickPhase.AWAITING_KICK ||
                PenaltyShootoutClient.kickPhase == PenaltyKickPhase.RESOLVING
        }
        return canUseDiveAndCatch(player)
    }

    /**
     * 比赛期间守门员捡球 / 鱼跃扑救是否可用（须在己方大禁区内）。
     * 比赛未开始或赛前准备阶段无区域限制。
     */
    fun canUseDiveAndCatch(player: LocalPlayer): Boolean {
        if (!canUseGoalkeeperActions()) {
            return false
        }
        if (!MatchState.isDuringMatch()) {
            return true
        }
        return MatchFieldAreaUtil.isPlayerInPenaltyArea(player, localPlayerTeam(player))
    }

    fun canOperateFootball(player: LocalPlayer, level: Level): Boolean {
        if (!canShowFootballHints(player)) {
            return false
        }
        return footballHintKeys().any { canUseFootballHint(player, level, it) }
    }

    fun canShowFootballHints(player: LocalPlayer): Boolean {
        if (player.isSpectator || !player.mainHandItem.isEmpty) return false
        if (SetPieceClient.isMovementFrozen(player.uuid)) return true
        return true
    }

    fun canUseFootballHint(player: LocalPlayer, level: Level, key: KeyMapping): Boolean {
        if (!canShowFootballHints(player)) {
            return false
        }

        val throwInTaker = isThrowInTaker(player)
        val holdingBall = GoalkeeperStateClient.isHoldingBall ||
            (throwInTaker && SetPieceClient.isMovementFrozen(player.uuid))
        val canGk = canUseGoalkeeperActions()
        val kickoffLocked = MatchStartClient.isLocked
        if (isBlockedByGoalKickSetPiece(player, key)) {
            return false
        }
        if (isBlockedByThrowInSetPiece(player, key)) {
            return false
        }
        if (isBlockedByPassOnlySetPiece(player, key)) {
            return false
        }
        if (isPenaltyKicker(player)) {
            if (!isPenaltyKickerAwaitingKick(player)) return false
            return when (key) {
                FootballKeyBindings.KICK ->
                    !kickoffLocked && hasBallWithinRange(player, level, FootballInputConfig.PLAYER_KICK_RANGE)
                else -> false
            }
        }

        val freeKickGkHolding = isFreeKickDefendingGoalkeeperHolding(player)
        if (holdingBall) {
            return when (key) {
                FootballKeyBindings.GK_DIVE ->
                    (freeKickGkHolding || !kickoffLocked) && GoalkeeperHoldActionPermissionsClient.canThrow &&
                        (!GoalkeeperStateClient.isHoldReleaseLocked() || throwInTaker) &&
                        (canGk || throwInTaker)
                FootballKeyBindings.GK_CATCH ->
                    GoalkeeperHoldActionPermissionsClient.canDrop &&
                        (!GoalkeeperStateClient.isHoldReleaseLocked() || throwInTaker) &&
                        (canUseGoalKickDrop(player) ||
                            freeKickGkHolding ||
                            (!kickoffLocked && canGk && !throwInTaker))
                FootballKeyBindings.BOOST_SPRINT -> player.isSprinting && StaminaClient.stamina > 0f
                FootballKeyBindings.INTERRUPT_CHARGE -> FootballInputHandler.isAnyChargeActive()
                FootballKeyBindings.LOOK_AROUND -> true
                else -> false
            }
        }

        return when (key) {
            FootballKeyBindings.KICK ->
                !kickoffLocked && hasBallWithinRange(player, level, FootballInputConfig.PLAYER_KICK_RANGE)
            FootballKeyBindings.DRIBBLE ->
                !kickoffLocked &&
                    !SlideTackleStateClient.isSliding(player.id) &&
                    hasBallWithinRange(player, level, FootballInputConfig.PLAYER_KICK_RANGE)
            FootballKeyBindings.TRAP ->
                !kickoffLocked && hasBallWithinRange(player, level, FootballInputConfig.PLAYER_KICK_RANGE)
            FootballKeyBindings.CHIP ->
                !kickoffLocked && hasBallWithinRange(player, level, FootballInputConfig.PLAYER_KICK_RANGE)
            FootballKeyBindings.GK_DIVE ->
                !kickoffLocked && canGk && (canPrepareGoalkeeperDiveCharge(player) || canExecuteGoalkeeperDive(player))
            FootballKeyBindings.GK_CATCH ->
                !kickoffLocked && ((canGk && GoalkeeperHoldActionPermissionsClient.canCatch &&
                    canUseDiveAndCatch(player) &&
                    hasBallWithinRange(player, level, goalkeeperCatchRange(player))) ||
                    (canUseGoalKickCatch(player) &&
                        GoalkeeperHoldActionPermissionsClient.canCatch &&
                        hasBallWithinRange(player, level, goalkeeperCatchRange(player))))
            FootballKeyBindings.SLIDE_TACKLE -> FootballInputHandler.canSlideTackle(player.level().gameTime)
            FootballKeyBindings.BOOST_SPRINT -> player.isSprinting && StaminaClient.stamina > 0f
            FootballKeyBindings.INTERRUPT_CHARGE -> FootballInputHandler.isAnyChargeActive()
            FootballKeyBindings.LOOK_AROUND -> true
            else -> false
        }
    }

    private fun footballHintKeys(): List<KeyMapping> = listOf(
        FootballKeyBindings.KICK,
        FootballKeyBindings.DRIBBLE,
        FootballKeyBindings.TRAP,
        FootballKeyBindings.CHIP,
        FootballKeyBindings.GK_DIVE,
        FootballKeyBindings.GK_CATCH,
        FootballKeyBindings.SLIDE_TACKLE,
        FootballKeyBindings.BOOST_SPRINT,
        FootballKeyBindings.INTERRUPT_CHARGE,
        FootballKeyBindings.LOOK_AROUND,
    )

    private fun goalkeeperCatchRange(player: LocalPlayer): Double {
        var range = GoalkeeperInputConfig.GK_CATCH_RANGE
        if (player.isShiftKeyDown) {
            range += GoalkeeperInputConfig.GK_CROUCH_RANGE_BONUS
        }
        return range
    }

    private fun canUseGoalKickCatch(player: LocalPlayer): Boolean {
        if (SetPieceClient.kind != SetPieceKind.GOAL_KICK) return false
        if (SetPieceClient.restartTeam != MatchStartClient.playerTeam) return false
        return SetPieceClient.goalKickPhase == GoalKickPhase.WAITING_PICKUP
    }

    private fun canUseGoalKickDrop(player: LocalPlayer): Boolean {
        if (SetPieceClient.kind != SetPieceKind.GOAL_KICK) return false
        if (SetPieceClient.goalKickPhase != GoalKickPhase.PLACING) return false
        if (SetPieceClient.goalKickPickerUuid != player.uuid) return false
        val goalAreaSide = SetPieceClient.defendingSide ?: SetPieceClient.restartTeam ?: return false
        return MatchFieldAreaUtil.isPlayerInGoalArea(player, goalAreaSide)
    }

    private fun isFreeKickDefendingGoalkeeperHolding(player: LocalPlayer): Boolean {
        if (SetPieceClient.kind != SetPieceKind.FREE_KICK) return false
        if (!GoalkeeperStateClient.isHoldingBall) return false
        if (!GoalkeeperStateClient.isGoalkeeper) return false
        return SetPieceClient.restartTeam != localPlayerTeam(player)
    }

    fun isThrowInTaker(player: LocalPlayer): Boolean =
        SetPieceClient.kind == SetPieceKind.THROW_IN &&
            SetPieceClient.throwInTakerUuid == player.uuid

    private fun isBlockedByThrowInSetPiece(player: LocalPlayer, key: KeyMapping): Boolean {
        if (SetPieceClient.kind != SetPieceKind.THROW_IN) return false
        if (isThrowInTaker(player)) {
            if (MatchStartClient.isLocked) {
                return key != FootballKeyBindings.LOOK_AROUND
            }
            return when (key) {
                FootballKeyBindings.GK_DIVE,
                FootballKeyBindings.LOOK_AROUND,
                -> false
                else -> true
            }
        }
        if (SetPieceClient.restartTeam == MatchStartClient.playerTeam) {
            return key != FootballKeyBindings.BOOST_SPRINT && key != FootballKeyBindings.LOOK_AROUND
        }
        return false
    }

    private fun isBlockedByGoalKickSetPiece(player: LocalPlayer, key: KeyMapping): Boolean {
        if (SetPieceClient.kind != SetPieceKind.GOAL_KICK) return false
        val isRestartTeam = SetPieceClient.restartTeam == MatchStartClient.playerTeam
        val isPicker = SetPieceClient.goalKickPickerUuid == player.uuid
        val isCatchKey = key == FootballKeyBindings.GK_CATCH
        return when (SetPieceClient.goalKickPhase) {
            GoalKickPhase.WAITING_PICKUP -> {
                if (isRestartTeam && isCatchKey) false else key != FootballKeyBindings.BOOST_SPRINT &&
                    key != FootballKeyBindings.LOOK_AROUND
            }
            GoalKickPhase.PLACING -> {
                if (isPicker && isCatchKey) false else key != FootballKeyBindings.BOOST_SPRINT &&
                    key != FootballKeyBindings.LOOK_AROUND
            }
            GoalKickPhase.PLACED -> {
                if (isRestartTeam) {
                    if (MatchStartClient.kickoffTouched) return false
                    return key != FootballKeyBindings.KICK &&
                        key != FootballKeyBindings.BOOST_SPRINT &&
                        key != FootballKeyBindings.LOOK_AROUND
                }
                key != FootballKeyBindings.BOOST_SPRINT &&
                    key != FootballKeyBindings.LOOK_AROUND
            }
            null -> false
        }
    }

    private fun isBlockedByPassOnlySetPiece(player: LocalPlayer, key: KeyMapping): Boolean {
        if (MatchStartClient.kickoffTouched) return false
        val team = MatchStartClient.playerTeam
        val passOnlyAllowed = setOf(
            FootballKeyBindings.KICK,
            FootballKeyBindings.BOOST_SPRINT,
            FootballKeyBindings.LOOK_AROUND,
            FootballKeyBindings.INTERRUPT_CHARGE,
        )
        val teammateIdleAllowed = setOf(
            FootballKeyBindings.BOOST_SPRINT,
            FootballKeyBindings.LOOK_AROUND,
        )
        return when (SetPieceClient.kind) {
            SetPieceKind.CORNER_KICK -> {
                if (SetPieceClient.restartTeam != team) return false
                if (player.uuid == SetPieceClient.cornerKickTakerUuid) {
                    return key !in passOnlyAllowed
                }
                return key !in teammateIdleAllowed
            }
            SetPieceKind.FREE_KICK -> {
                if (SetPieceClient.restartTeam != team) return false
                if (player.uuid == SetPieceClient.freeKickTakerUuid) {
                    return key !in passOnlyAllowed
                }
                return key !in teammateIdleAllowed
            }
            SetPieceKind.CENTER_KICKOFF -> {
                if (SetPieceClient.restartTeam != team) return false
                return key !in passOnlyAllowed
            }
            else -> false
        }
    }

    private fun hasBallWithinRange(player: LocalPlayer, level: Level, range: Double): Boolean =
        nearestOperableFootball(player, level, range) != null

    fun nearestOperableFootball(player: LocalPlayer, level: Level, range: Double): Football? {
        return level.getEntitiesOfClass(
            Football::class.java,
            player.boundingBox.inflate(range),
        )
            .filter { !it.isHeld() && !it.isPlayerBallMovementForbidden(player) && player.distanceToSqr(it) <= range * range }
            .minByOrNull { it.distanceToSqr(player) }
    }
}
