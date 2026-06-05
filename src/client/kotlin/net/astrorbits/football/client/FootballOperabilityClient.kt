package net.astrorbits.football.client

import net.astrorbits.football.client.key.FootballInputHandler
import net.astrorbits.football.client.key.FootballKeyBindings
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.Football
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.astrorbits.football.match.MatchFieldAreaUtil
import net.astrorbits.football.match.MatchState
import net.minecraft.client.KeyMapping
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.level.Level

object FootballOperabilityClient {
    /** 比赛进行中仅守门员可用守门员场地操作；比赛未开始（含准备/结算）时所有球员可用。 */
    fun canUseGoalkeeperActions(): Boolean =
        GoalkeeperStateClient.isGoalkeeper || !MatchState.isDuringMatch()

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
        return MatchFieldAreaUtil.isPlayerInPenaltyArea(player, MatchStartClient.playerTeam)
    }

    fun canOperateFootball(player: LocalPlayer, level: Level): Boolean {
        if (!canShowFootballHints(player)) {
            return false
        }
        return footballHintKeys().any { canUseFootballHint(player, level, it) }
    }

    fun canShowFootballHints(player: LocalPlayer): Boolean =
        !player.isSpectator && player.mainHandItem.isEmpty && !MatchStartClient.isLocked

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
            FootballKeyBindings.GK_DIVE -> canGk && canUseDiveAndCatch(player)
            FootballKeyBindings.GK_CATCH ->
                canGk && GoalkeeperHoldActionPermissionsClient.canCatch &&
                    canUseDiveAndCatch(player) &&
                    hasBallWithinRange(player, level, goalkeeperCatchRange(player))
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
