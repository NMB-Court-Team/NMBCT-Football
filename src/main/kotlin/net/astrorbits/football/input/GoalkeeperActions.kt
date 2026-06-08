package net.astrorbits.football.input

import net.astrorbits.football.Football
import net.astrorbits.football.FootballParticles
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.config.FootballConfigs
import net.astrorbits.football.match.FreeKickSetPieceFlow
import net.astrorbits.football.match.GoalKickSetPieceFlow
import net.astrorbits.football.match.GoalkeeperCatchFoulDetector
import net.astrorbits.football.match.SetPieceRestrictionCoordinator
import net.astrorbits.football.match.ThrowInSetPieceFlow
import net.astrorbits.football.network.FootballActionC2SPayload
import net.astrorbits.football.network.FootballActionType
import net.astrorbits.football.stamina.StaminaState
import net.astrorbits.football.util.FootballKickUtil
import net.astrorbits.football.util.GoalkeeperUtil
import net.astrorbits.football.util.KickChargeUtil
import net.astrorbits.football.util.Vec3Math
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object GoalkeeperActions {
    private const val CATCH_RECOIL_SCALE = 0.2
    private const val CATCH_MAX_RECOIL = 0.75
    private const val CATCH_FORWARD_DAMP = 0.15
    private val lastActionTick = ConcurrentHashMap<UUID, Long>()
    private val diveCooldownUntil = ConcurrentHashMap<UUID, Long>()

    fun handle(player: ServerPlayer, payload: FootballActionC2SPayload) {
        if (!net.astrorbits.football.match.MatchParticipation.isParticipating(player)) {
            return
        }
        if (!GoalkeeperActionAccess.canUseGoalkeeperFieldActions(player) &&
            !net.astrorbits.football.match.PenaltyShootoutState.isDefendingGoalkeeper(player) &&
            !net.astrorbits.football.match.MatchPenaltyKickState.isDefendingGoalkeeper(player) &&
            !SetPieceRestrictionCoordinator.allowsCatchDespiteRole(player) &&
            !SetPieceRestrictionCoordinator.allowsGoalKickDrop(player) &&
            !ThrowInSetPieceFlow.isMovementFrozen(player)
        ) {
            return
        }
        if (SetPieceRestrictionCoordinator.isFootballOperationBlocked(player, payload.action)) {
            return
        }
        // 服务端双重保险：非发球方球员在开球锁定时拒绝所有足球操作
        if (net.astrorbits.football.match.MatchState.isKickoffInteractionLocked(player, payload.action)) return
        if (!defersThrowInKickoffTouchNotify(player, payload.action)) {
            net.astrorbits.football.match.MatchState.tryNotifyKickoffBallTouched(player)
        }
        FootballDribbleSessions.end(player)

        if (!canAct(player)) {
            return
        }

        val heldBall = GoalkeeperUtil.findHeldFootball(player)
        if (heldBall != null) {
            handleWhileHolding(player, heldBall, payload)
            return
        }

        if (GoalkeeperDiveSessions.isDiving(player)) {
            return
        }

        if (requiresOwnPenaltyArea(payload.action) &&
            !passesDiveAreaGate(player, payload.action)
        ) {
            return
        }

        when (payload.action) {
            FootballActionType.GK_CATCH -> {
                if (GoalkeeperHoldActionPermissions.canCatch(player)) {
                    handleCatch(player)
                }
            }
            FootballActionType.GK_DIVE -> handleDive(player, payload)
            FootballActionType.GK_DIVE_CHARGE_DRAIN -> handleDiveChargeDrain(player)
            FootballActionType.GK_DIVE_CHARGE_CANCEL -> handleDiveChargeCancel(player)
            else -> Unit
        }
    }

    fun handleDiveChargeDrain(player: ServerPlayer) {
        if (!canAct(player) || GoalkeeperUtil.findHeldFootball(player) != null) {
            return
        }
        val cfg = FootballConfigs.server.staminaMechanism
        StaminaState.tryConsume(player, cfg.gkDiveFullChargeHoldDrainPerTick())
    }

    fun handleDiveChargeCancel(player: ServerPlayer) {
        if (!canAct(player) || GoalkeeperUtil.findHeldFootball(player) != null) {
            return
        }
        val cfg = FootballConfigs.server.staminaMechanism
        StaminaState.tryConsume(player, cfg.gkDiveChargeCancelCost)
    }

    private fun handleWhileHolding(player: ServerPlayer, football: Football, payload: FootballActionC2SPayload) {
        val now = player.level().gameTime

        when (payload.action) {
            FootballActionType.GK_THROW_SHORT,
            FootballActionType.GK_THROW_LONG,
            -> {
                val throwInRelease = ThrowInSetPieceFlow.allowsThrowAction(player, payload.action)
                if (!GoalkeeperHoldActionPermissions.canThrow(player)) {
                    return
                }
                if (!throwInRelease && GoalkeeperHoldLock.isReleaseBlocked(player, now)) {
                    return
                }
                if (throwInRelease && !ThrowInSetPieceFlow.isInwardThrow(payload.lookYaw, payload.lookPitch)) {
                    applyLookFromPayload(player, payload)
                    football.syncHeldPose(player, payload.lookYaw, payload.lookPitch)
                    ThrowInSetPieceFlow.onFoulThrow(player)
                    return
                }
            }
            FootballActionType.GK_DROP -> {
                if (!GoalkeeperHoldActionPermissions.canDrop(player) || GoalkeeperHoldLock.isReleaseBlocked(player, now)) {
                    return
                }
                if (!GoalKickSetPieceFlow.canDropInGoalArea(player)) {
                    return
                }
            }
            else -> Unit
        }

        if (!tryConsumeCooldown(player, now)) {
            return
        }

        applyLookFromPayload(player, payload)
        football.syncHeldPose(player, payload.lookYaw, payload.lookPitch)

        when (payload.action) {
            FootballActionType.GK_THROW_SHORT -> {
                val params = GoalkeeperUtil.resolveThrowShortParams()
                FootballKickUtil.recordActiveKickForPlayerLook(
                    player, football, payload.lookYaw, payload.lookPitch, params,
                )
                if (FootballKickUtil.applyKickToFootballWithLook(
                    football,
                    params,
                    payload.lookYaw,
                    payload.lookPitch,
                    random = player.random,
                    spreadInaccuracy = FootballInputConfig.KICK_SPREAD_INACCURACY,
                    actingPlayer = player,
                )) {
                    FootballParticles.playGkThrow(player, football)
                    FootballSounds.playGkThrow(player)
                    lastActionTick[player.uuid] = now
                    ThrowInSetPieceFlow.onBallThrown(player)
                    FreeKickSetPieceFlow.onDefendingGoalkeeperDistributed(player)
                }
            }
            FootballActionType.GK_THROW_LONG -> {
                val sprinting = payload.flags and FootballInputConfig.FLAG_SPRINT != 0
                val chargeSettings = FootballInputConfig.chargeSettings()
                val perfect = KickChargeUtil.isPerfectCharge(payload.chargeHeldMs, chargeSettings)
                val chargeRatio = KickChargeUtil.computeRatio(payload.chargeHeldMs, chargeSettings)
                val params = GoalkeeperUtil.resolveThrowLongParams(chargeRatio, sprinting, perfect)
                FootballKickUtil.recordActiveKickForPlayerLook(
                    player, football, payload.lookYaw, payload.lookPitch, params,
                )
                if (FootballKickUtil.applyKickToFootballWithLook(
                    football,
                    params,
                    payload.lookYaw,
                    payload.lookPitch,
                    random = if (!perfect) player.random else null,
                    spreadInaccuracy = if (!perfect) FootballInputConfig.KICK_SPREAD_INACCURACY else 0.0,
                    actingPlayer = player,
                )) {
                    FootballParticles.playGkThrow(player, football)
                    FootballSounds.playGkThrow(player)
                    lastActionTick[player.uuid] = now
                    ThrowInSetPieceFlow.onBallThrown(player)
                    FreeKickSetPieceFlow.onDefendingGoalkeeperDistributed(player)
                }
            }
            FootballActionType.GK_DROP -> {
                if (football.isPlayerBallMovementForbidden(player) &&
                    !SetPieceRestrictionCoordinator.allowsGoalKickDrop(player) &&
                    !SetPieceRestrictionCoordinator.allowsFreeKickDefendingGoalkeeperHoldAction(
                        player,
                        FootballActionType.GK_DROP,
                    )
                ) {
                    return
                }
                football.recordActiveKick(player, null)
                football.dropAt(player)
                FootballParticles.playGkCatch(player, football, 0.0)
                FootballSounds.playGkCatch(player, 0.0)
                lastActionTick[player.uuid] = now
                GoalKickSetPieceFlow.onBallDropped(player)
                FreeKickSetPieceFlow.onDefendingGoalkeeperDistributed(player)
            }
            else -> Unit
        }
    }

    private fun handleCatch(player: ServerPlayer) {
        val now = player.level().gameTime
        if (!tryConsumeCooldown(player, now)) {
            return
        }

        val range = GoalkeeperUtil.catchRange(player)
        val football = FootballKickUtil.findNearestFootball(player, range) ?: return
        if (football.isHeld()) {
            return
        }
        if (football.isPlayerBallMovementForbidden(player) &&
            !SetPieceRestrictionCoordinator.allowsGoalKickCatch(player)
        ) {
            return
        }
        if (GoalkeeperUtil.standingCatchDistanceSqr(player, football) > range * range) {
            return
        }

        val speed = GoalkeeperUtil.ballSpeed(football)
        if (speed > GoalkeeperInputConfig.GK_CATCH_MAX_SPEED) {
            return
        }
        if (!GoalkeeperUtil.canStandingCatchBall(football, player)) {
            return
        }

        val incoming = football.getPhysicsState().linearVelocity
        football.recordActiveKick(player, null)
        football.enterHold(player)
        if (!football.isHeldBy(player)) {
            return
        }
        if (GoalkeeperCatchFoulDetector.tryAwardCatchOutsidePenaltyArea(player, football)) {
            lastActionTick[player.uuid] = now
            return
        }
        val recoil = computeCatchRecoil(speed, incoming)
        applyCatchMomentumDamping(player, player.lookAngle, recoil)
        FootballSounds.playGkCatch(player, speed)
        FootballParticles.playGkCatch(player, football, speed)
        lastActionTick[player.uuid] = now
        val ctx = net.astrorbits.football.match.SetPieceState.active
        if (ctx?.kind == net.astrorbits.football.match.SetPieceKind.GOAL_KICK) {
            when (ctx.goalKickPhase) {
                net.astrorbits.football.match.GoalKickPhase.WAITING_PICKUP ->
                    GoalKickSetPieceFlow.onPlayerCaughtBall(player, football)
                net.astrorbits.football.match.GoalKickPhase.PLACED -> {
                    val server = player.level().server
                    if (!GoalKickSetPieceFlow.tryRestartOnGoalKickTouch(player, server)) {
                        GoalKickSetPieceFlow.onBallMoved(server, football, player)
                    } else {
                        football.releaseHold()
                    }
                }
                net.astrorbits.football.match.GoalKickPhase.AWAITING_PA_EXIT -> {
                    val server = player.level().server
                    if (GoalKickSetPieceFlow.tryRestartOnAwaitingExitTouch(player, server)) {
                        football.releaseHold()
                    }
                }
                else -> Unit
            }
        }
    }

    private fun handlePunch(player: ServerPlayer) {
        val now = player.level().gameTime
        if (!tryConsumeCooldown(player, now)) {
            return
        }

        val range = GoalkeeperUtil.punchRange(player)
        val football = FootballKickUtil.findNearestFootball(player, range) ?: return
        if (football.isHeld()) {
            return
        }
        if (football.isPlayerBallMovementForbidden(player)) {
            return
        }
        if (player.distanceToSqr(football) > range * range) {
            return
        }

        if (FootballKickUtil.applyKickToFootball(player, football, GoalkeeperUtil.resolvePunchParams())) {
            FootballSounds.playGkPunch(player)
            FootballParticles.playGkPunch(player, football)
            lastActionTick[player.uuid] = now
        }
    }

    private fun handleDive(player: ServerPlayer, payload: FootballActionC2SPayload) {
        val now = player.level().gameTime
        val cooldownUntil = diveCooldownUntil[player.uuid] ?: 0L
        if (now < cooldownUntil) {
            return
        }

        var direction = GoalkeeperUtil.resolveDiveLookDirection(payload.lookYaw, payload.lookPitch)
        if (direction.lengthSqr() < 1.0e-8) {
            direction = GoalkeeperUtil.resolveDiveDirection(player, useLookOnly = true)
        }
        if (direction.lengthSqr() < 1.0e-8) {
            return
        }

        val chargeRatio = GoalkeeperUtil.resolveDiveChargeRatio(payload.chargeHeldMs, payload.chargeRatio)
        GoalkeeperDiveSessions.begin(player, direction, chargeRatio, payload.lookPitch, now)
        FootballSounds.playGkDive(player)
        FootballParticles.playGkDive(player)
        diveCooldownUntil[player.uuid] = now + GoalkeeperInputConfig.GK_DIVE_COOLDOWN_TICKS
    }

    fun tryResolveDiveDeflect(player: ServerPlayer, football: Football): Boolean {
        if (football.isPlayerBallMovementForbidden(player) || football.isHeld()) {
            return false
        }

        val unitDir = GoalkeeperUtil.diveCatchDeflectDirection(player, football)
        if (unitDir.lengthSqr() < 1.0e-8) {
            return false
        }

        val params = GoalkeeperUtil.resolvePunchParams()
        val horizontal = Vec3Math.horizontal(unitDir)
        val kickDirection = if (horizontal.lengthSqr() > 1.0e-8) {
            FootballKickUtil.buildKickDirection(horizontal, unitDir, params.force, params.angleDegrees)
        } else {
            unitDir.scale(params.force)
        }

        football.recordActiveKick(player, kickDirection)
        val ballCenter = GoalkeeperUtil.ballCenter(football)
        val horizForPoint = if (horizontal.lengthSqr() > 1.0e-8) horizontal else Vec3Math.horizontal(player.lookAngle)
        val kickPoint = FootballKickUtil.buildKickPoint(ballCenter, horizForPoint, 0.0)
        if (!football.kick(kickPoint, kickDirection, actingPlayer = player)) {
            return false
        }

        net.astrorbits.football.match.MatchState.tryNotifyKickoffBallTouched(player)
        FootballSounds.playGkPunch(player)
        FootballParticles.playGkPunch(player, football)
        return true
    }

    fun tryResolveDiveCatch(player: ServerPlayer, football: Football, diveDirection: Vec3): Boolean {
        if (football.isPlayerBallMovementForbidden(player)) {
            return false
        }
        val speed = GoalkeeperUtil.ballSpeed(football)
        if (speed > GoalkeeperInputConfig.GK_DIVE_CATCH_MAX_SPEED) {
            return false
        }
        football.recordActiveKick(player, diveDirection)
        val incoming = football.getPhysicsState().linearVelocity
        if (football.isHeld()) {
            return false
        }
        football.enterHold(player)
        if (!football.isHeldBy(player)) {
            return false
        }
        if (GoalkeeperCatchFoulDetector.tryAwardCatchOutsidePenaltyArea(player, football)) {
            return true
        }
        applyCatchMomentumDamping(player, diveDirection, computeCatchRecoil(speed, incoming))

        FootballSounds.playGkCatch(player, speed)
        FootballParticles.playGkCatch(player, football, speed)
        return true
    }

    private fun computeCatchRecoil(speed: Double, incoming: Vec3): Vec3 {
        if (speed < GoalkeeperInputConfig.GK_DIVE_CATCH_RECOIL_MIN_SPEED) {
            return Vec3.ZERO
        }
        val recoilImpulse = incoming.scale(GoalkeeperInputConfig.GK_DIVE_DEFLECT_FORCE_SCALE * CATCH_RECOIL_SCALE)
        val recoilLength = recoilImpulse.length()
        if (recoilLength > CATCH_MAX_RECOIL && recoilLength > 1.0e-8) {
            return recoilImpulse.scale(CATCH_MAX_RECOIL / recoilLength)
        }
        return recoilImpulse
    }

    private fun applyCatchMomentumDamping(player: ServerPlayer, moveDirection: Vec3, recoil: Vec3) {
        val horizontalDir = Vec3Math.normalizeSafe(Vec3(moveDirection.x, 0.0, moveDirection.z))
        val current = player.deltaMovement
        val forwardComponent = if (horizontalDir.lengthSqr() > 1.0e-8) {
            horizontalDir.scale(current.dot(horizontalDir))
        } else {
            Vec3.ZERO
        }
        val remaining = current.subtract(forwardComponent)
        val dampedForward = forwardComponent.scale(CATCH_FORWARD_DAMP)
        player.setDeltaMovement(remaining.add(dampedForward).add(recoil))
        player.hurtMarked = true
    }

    private fun applyLookFromPayload(player: ServerPlayer, payload: FootballActionC2SPayload) {
        player.yRot = payload.lookYaw
        player.xRot = payload.lookPitch
        player.yHeadRot = payload.lookYaw
        player.yBodyRot = payload.lookYaw
    }

    private fun tryConsumeCooldown(player: ServerPlayer, now: Long): Boolean {
        val last = lastActionTick[player.uuid] ?: -1
        return now - last >= GoalkeeperInputConfig.GK_ACTION_COOLDOWN_TICKS
    }

    private fun canAct(player: Player): Boolean = player.mainHandItem.isEmpty

    private fun passesDiveAreaGate(player: ServerPlayer, action: FootballActionType): Boolean {
        if (SetPieceRestrictionCoordinator.allowsGoalKickCatch(player)) {
            return true
        }
        return when (action) {
            FootballActionType.GK_DIVE_CHARGE_DRAIN,
            FootballActionType.GK_DIVE_CHARGE_CANCEL,
            -> GoalkeeperActionAccess.canPrepareGoalkeeperDiveCharge(player)
            FootballActionType.GK_DIVE -> GoalkeeperActionAccess.canExecuteGoalkeeperDive(player)
            FootballActionType.GK_CATCH -> GoalkeeperActionAccess.canUseDiveAndCatch(player)
            else -> true
        }
    }

    private fun requiresOwnPenaltyArea(action: FootballActionType): Boolean = when (action) {
        FootballActionType.GK_CATCH,
        FootballActionType.GK_DIVE,
        FootballActionType.GK_DIVE_CHARGE_DRAIN,
        FootballActionType.GK_DIVE_CHARGE_CANCEL,
        -> true
        else -> false
    }

    /** 界外球主罚员合法抛球后才算触球，避免往外扔犯规时误解除开球锁。 */
    private fun defersThrowInKickoffTouchNotify(player: ServerPlayer, action: FootballActionType): Boolean {
        if (action != FootballActionType.GK_THROW_SHORT && action != FootballActionType.GK_THROW_LONG) {
            return false
        }
        return ThrowInSetPieceFlow.isMovementFrozen(player)
    }
}
