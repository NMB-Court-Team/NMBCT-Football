package net.astrorbits.football.client

import net.astrorbits.football.client.key.FootballInputHandler
import net.astrorbits.football.client.key.FootballKeyBindings
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.client.match.PenaltyShootoutClient
import net.astrorbits.football.Football
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.astrorbits.football.match.GoalKickPhase
import net.astrorbits.football.match.MatchFieldAreaUtil
import net.astrorbits.football.match.MatchPhase
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.PenaltyKickPhase
import net.astrorbits.football.match.SetPieceKind
import net.astrorbits.football.match.TeamSide
import net.astrorbits.football.client.SetPieceClient
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
        return !MatchStartClient.isLocked
    }

    fun canUseFootballHint(player: LocalPlayer, level: Level, key: KeyMapping): Boolean {
        if (!canShowFootballHints(player)) {
            return false
        }

        val holdingBall = GoalkeeperStateClient.isHoldingBall
        val canGk = canUseGoalkeeperActions()

        if (holdingBall) {
            return when (key) {
                FootballKeyBindings.GK_DIVE ->
                    canGk && GoalkeeperHoldActionPermissionsClient.canThrow &&
                        !GoalkeeperStateClient.isHoldReleaseLocked()
                FootballKeyBindings.GK_CATCH ->
                    canGk && GoalkeeperHoldActionPermissionsClient.canDrop &&
                        !GoalkeeperStateClient.isHoldReleaseLocked()
                FootballKeyBindings.BOOST_SPRINT -> player.isSprinting && StaminaClient.stamina > 0f
                FootballKeyBindings.INTERRUPT_CHARGE -> FootballInputHandler.isAnyChargeActive()
                FootballKeyBindings.LOOK_AROUND -> true
                else -> false
            }
        }

        return when (key) {
            FootballKeyBindings.KICK ->
                hasBallWithinRange(player, level, FootballInputConfig.PLAYER_KICK_RANGE)
            FootballKeyBindings.DRIBBLE ->
                hasBallWithinRange(player, level, FootballInputConfig.PLAYER_KICK_RANGE)
            FootballKeyBindings.TRAP ->
                hasBallWithinRange(player, level, FootballInputConfig.PLAYER_KICK_RANGE)
            FootballKeyBindings.CHIP ->
                hasBallWithinRange(player, level, FootballInputConfig.PLAYER_KICK_RANGE)
            FootballKeyBindings.GK_DIVE ->
                canGk && (canPrepareGoalkeeperDiveCharge(player) || canExecuteGoalkeeperDive(player))
            FootballKeyBindings.GK_CATCH ->
                (canGk && GoalkeeperHoldActionPermissionsClient.canCatch &&
                    canUseDiveAndCatch(player) &&
                    hasBallWithinRange(player, level, goalkeeperCatchRange(player))) ||
                    (canUseGoalKickCatch(player) &&
                        GoalkeeperHoldActionPermissionsClient.canCatch &&
                        hasBallWithinRange(player, level, goalkeeperCatchRange(player)))
            FootballKeyBindings.SLIDE_TACKLE -> FootballInputHandler.canSlideTackle(player.level()?.gameTime ?: 0L)
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
        return when (SetPieceClient.goalKickPhase) {
            GoalKickPhase.WAITING_PICKUP -> true
            GoalKickPhase.PLACED -> GoalkeeperStateClient.isGoalkeeper
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
