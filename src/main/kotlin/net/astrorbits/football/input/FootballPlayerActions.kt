package net.astrorbits.football.input

import net.astrorbits.football.Football
import net.astrorbits.football.FootballParticles
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.item.FootballItem
import net.astrorbits.football.match.MatchParticipation
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.SetPieceRestrictionCoordinator
import net.astrorbits.football.match.ThrowInSetPieceFlow
import net.astrorbits.football.network.FootballActionC2SPayload
import net.astrorbits.football.network.FootballActionType
import net.astrorbits.football.util.FootballKickUtil
import net.astrorbits.football.util.GoalkeeperUtil
import net.astrorbits.football.util.KickChargeUtil
import net.astrorbits.football.util.Vec3Math
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object FootballPlayerActions {
    private val lastActionTick = ConcurrentHashMap<UUID, Long>()
    private val lastKickAwayActionTick = ConcurrentHashMap<UUID, Long>()

    /** 踢球后在此 tick 数内，若球仍明显远离玩家则拒绝恢复运球。 */
    private const val DRIBBLE_RESUME_VELOCITY_CHECK_TICKS = 20L
    private const val DRIBBLE_RESUME_MIN_BALL_SPEED_SQR = 0.01

    fun handle(player: ServerPlayer, payload: FootballActionC2SPayload) {
        if (!MatchParticipation.isParticipating(player)) {
            return
        }
        if (GoalkeeperUtil.findHeldFootball(player) != null) {
            when (payload.action) {
                FootballActionType.GK_THROW_SHORT,
                FootballActionType.GK_THROW_LONG,
                FootballActionType.GK_DROP,
                -> {
                    if (!canRouteToGoalkeeperHoldActions(player)) {
                        return
                    }
                    GoalkeeperActions.handle(player, payload)
                }
                else -> Unit
            }
            return
        }
        when (payload.action) {
            FootballActionType.SLIDE_TACKLE -> {
                handleSlideTackle(player, payload)
                return
            }
            FootballActionType.SLIDE_TACKLE_END -> {
                handleSlideTackleEnd(player)
                return
            }
            FootballActionType.GK_DIVE_CHARGE_DRAIN,
            FootballActionType.GK_DIVE_CHARGE_CANCEL,
            FootballActionType.GK_CATCH,
            FootballActionType.GK_DIVE,
            FootballActionType.GK_THROW_SHORT,
            FootballActionType.GK_THROW_LONG,
            FootballActionType.GK_DROP,
            -> {
                if (!canRouteToGoalkeeperHoldActions(player)) {
                    return
                }
                GoalkeeperActions.handle(player, payload)
                return
            }
            else -> Unit
        }

        if (SetPieceRestrictionCoordinator.isFootballOperationBlocked(player, payload.action)) return
        if (MatchState.isKickoffInteractionLocked(player, payload.action)) return
        MatchState.tryNotifyKickoffBallTouched(player)
        if (payload.action == FootballActionType.ITEM_THROW) {
            FootballItem.tryThrowFromMainHand(player)
            return
        }
        if (isBlockedBySlideState(player, payload.action)) {
            FootballDribbleSessions.end(player)
            return
        }

        when (payload.action) {
            FootballActionType.DRIBBLE_HOLD -> handleDribbleHold(player, payload)
            FootballActionType.DRIBBLE_END -> FootballDribbleSessions.end(player)
            else -> handleKickAction(player, payload)
        }
    }

    private fun isBlockedBySlideState(player: ServerPlayer, action: FootballActionType): Boolean {
        if (!SlideTackleSessions.isSliding(player)) {
            return false
        }
        return when (action) {
            FootballActionType.SLIDE_TACKLE_END,
            FootballActionType.SLIDE_TACKLE,
            FootballActionType.DRIBBLE_END,
            -> false
            else -> true
        }
    }

    private fun handleSlideTackle(player: ServerPlayer, payload: FootballActionC2SPayload) {
        if (MatchState.isPenaltyKickSetPieceActive()) {
            return
        }
        val now = player.level().gameTime
        if (SlideTackleSessions.isSliding(player)) {
            SlideTackleSessions.requestEnd(player, now)
            return
        }
        if (
            !canAct(player) ||
            !player.isSprinting ||
            !player.onGround() ||
            GoalkeeperDiveSessions.isDiving(player) ||
            now < SlideTackleSessions.getCooldownUntilTick(player.uuid)
        ) {
            return
        }
        FootballDribbleSessions.end(player)
        SlideTackleSessions.begin(
            player = player,
            now = now,
        )
    }

    private fun handleSlideTackleEnd(player: ServerPlayer) {
        SlideTackleSessions.requestEnd(player, player.level().gameTime)
    }

    private fun handleDribbleHold(player: ServerPlayer, payload: FootballActionC2SPayload) {
        if (SlideTackleSessions.isSliding(player)) {
            FootballDribbleSessions.end(player)
            return
        }

        if (!canAct(player)) {
            FootballDribbleSessions.end(player)
            return
        }

        val football = FootballKickUtil.findNearestFootball(player, FootballInputConfig.PLAYER_KICK_RANGE)
        if (football == null) {
            FootballDribbleSessions.end(player)
            return
        }
        if (football.isPlayerBallMovementForbidden(player)) {
            FootballDribbleSessions.end(player)
            return
        }
        if (football.isHoldStealProtectedFrom(player)) {
            FootballDribbleSessions.end(player)
            return
        }

        if (player.distanceToSqr(football) > FootballInputConfig.PLAYER_KICK_RANGE * FootballInputConfig.PLAYER_KICK_RANGE) {
            FootballDribbleSessions.end(player)
            return
        }

        if (!hasDribbleMovementInput(player, payload)) {
            FootballDribbleSessions.end(player)
            return
        }

        val now = player.level().gameTime
        if (shouldBlockDribbleAfterKickAway(player, football, now)) {
            FootballDribbleSessions.end(player)
            return
        }

        FootballDribbleSessions.beginOrRefresh(player, football, now, payload)
    }

    private fun handleKickAction(player: ServerPlayer, payload: FootballActionC2SPayload) {
        FootballDribbleSessions.end(player)

        if (!canAct(player)) {
            return
        }

        val football = FootballKickUtil.findNearestFootball(player, FootballInputConfig.PLAYER_KICK_RANGE) ?: return

        if (football.isHeld() && football.isHoldStealProtectedFrom(player)) {
            return
        }
        if (football.isPlayerBallMovementForbidden(player)) {
            return
        }

        if (player.distanceToSqr(football) > FootballInputConfig.PLAYER_KICK_RANGE * FootballInputConfig.PLAYER_KICK_RANGE) {
            return
        }

        val now = player.level().gameTime
        val last = lastActionTick[player.uuid] ?: -1
        if (now - last < FootballInputConfig.ACTION_COOLDOWN_TICKS) {
            return
        }

        val sprinting = payload.flags and FootballInputConfig.FLAG_SPRINT != 0

        when (payload.action) {
            FootballActionType.PASS -> {
                val params = FootballKickUtil.resolvePassParams()
                if (FootballKickUtil.applyKickToFootball(player, football, params, applySpread = true)) {
                    FootballSounds.playKick(player, params.force)
                    FootballParticles.playKick(player, football, params.force)
                    markKickAwayAction(player, now)
                }
            }
            FootballActionType.SHOOT -> {
                val chargeSettings = FootballInputConfig.chargeSettings()
                val perfect = KickChargeUtil.isPerfectCharge(payload.chargeHeldMs, chargeSettings)
                val chargeRatio = KickChargeUtil.computeRatio(payload.chargeHeldMs, chargeSettings)
                val params = FootballKickUtil.resolveShootParams(chargeRatio, sprinting, perfect)
                if (FootballKickUtil.applyKickToFootball(player, football, params, applySpread = !perfect)) {
                    FootballSounds.playKick(player, params.force)
                    FootballParticles.playKick(player, football, params.force)
                    markKickAwayAction(player, now)
                    val look = FootballKickUtil.lookDirection(player.yRot, player.xRot)
                    KickCurveSessions.begin(
                        player,
                        football,
                        Vec3Math.horizontal(look),
                        chargeRatio,
                        now,
                    )
                }
            }
            FootballActionType.TRAP -> {
                football.recordActiveKick(player, null)
                if (football.trap(player)) {
                    FootballSounds.playTrap(player)
                    FootballParticles.playTrap(player, football)
                    lastActionTick[player.uuid] = now
                }
            }
            FootballActionType.CHIP -> {
                val params = FootballKickUtil.resolveChipParams(player)
                if (FootballKickUtil.applyKickToFootball(player, football, params)) {
                    FootballSounds.playKick(player, params.force)
                    FootballParticles.playKick(player, football, params.force)
                    markKickAwayAction(player, now)
                }
            }
            FootballActionType.DRIBBLE_HOLD, FootballActionType.DRIBBLE_END,
            FootballActionType.GK_CATCH, FootballActionType.GK_DIVE, FootballActionType.GK_PUNCH,
            FootballActionType.GK_THROW_SHORT, FootballActionType.GK_THROW_LONG, FootballActionType.GK_DROP,
            FootballActionType.ITEM_THROW, FootballActionType.SLIDE_TACKLE, FootballActionType.SLIDE_TACKLE_END,
            FootballActionType.GK_DIVE_CHARGE_DRAIN, FootballActionType.GK_DIVE_CHARGE_CANCEL,
            -> Unit
        }
    }

    private fun canAct(player: Player): Boolean = player.mainHandItem.isEmpty

    private fun hasDribbleMovementInput(player: ServerPlayer, payload: FootballActionC2SPayload): Boolean {
        if (SlideTackleSessions.isSliding(player)) {
            return true
        }
        if (payload.flags and FootballInputConfig.FLAG_LOOK_AROUND != 0) {
            return FootballMovementInputUtil.hasMovementInput(player, payload.lookYaw)
        }
        return FootballMovementInputUtil.hasMovementInput(player)
    }

    private fun markKickAwayAction(player: ServerPlayer, now: Long) {
        lastActionTick[player.uuid] = now
        lastKickAwayActionTick[player.uuid] = now
    }

    private fun shouldBlockDribbleAfterKickAway(player: ServerPlayer, football: Football, now: Long): Boolean {
        val lastKick = lastKickAwayActionTick[player.uuid] ?: return false
        if (now - lastKick > DRIBBLE_RESUME_VELOCITY_CHECK_TICKS) {
            return false
        }

        val toBall = Vec3Math.horizontal(football.position().subtract(player.position()))
        if (toBall.lengthSqr() < 1.0e-8) {
            return false
        }

        val speed = Vec3Math.horizontal(football.getPhysicsState().linearVelocity)
        if (speed.lengthSqr() < DRIBBLE_RESUME_MIN_BALL_SPEED_SQR) {
            return false
        }

        return toBall.normalize().dot(speed.normalize()) > 0.0
    }

    /** 持球后的守门员动作路由：含界外球主罚员（非门将也可抛球）。 */
    private fun canRouteToGoalkeeperHoldActions(player: ServerPlayer): Boolean =
        GoalkeeperActionAccess.canUseGoalkeeperFieldActions(player) ||
            net.astrorbits.football.match.PenaltyShootoutState.isDefendingGoalkeeper(player) ||
            net.astrorbits.football.match.MatchPenaltyKickState.isDefendingGoalkeeper(player) ||
            SetPieceRestrictionCoordinator.allowsCatchDespiteRole(player) ||
            SetPieceRestrictionCoordinator.allowsGoalKickDrop(player) ||
            ThrowInSetPieceFlow.isMovementFrozen(player)
}
