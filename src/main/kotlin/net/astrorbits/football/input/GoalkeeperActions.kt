package net.astrorbits.football.input

import net.astrorbits.football.Football
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.FootballParticles
import net.astrorbits.football.network.FootballActionC2SPayload
import net.astrorbits.football.network.FootballActionType
import net.astrorbits.football.config.FootballConfigs
import net.astrorbits.football.stamina.StaminaState
import net.astrorbits.football.util.FootballKickUtil
import net.astrorbits.football.util.GoalkeeperUtil
import net.astrorbits.football.util.KickChargeUtil
import net.astrorbits.football.util.Vec3Math
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object GoalkeeperActions {
    private const val CATCH_RECOIL_SCALE = 0.2
    private const val CATCH_MAX_RECOIL = 0.75
    private const val CATCH_FORWARD_DAMP = 0.15
    private val lastActionTick = ConcurrentHashMap<UUID, Long>()
    private val diveCooldownUntil = ConcurrentHashMap<UUID, Long>()

    fun handle(player: ServerPlayer, payload: FootballActionC2SPayload) {
        // 服务端双重保险：非发球方球员在开球锁定时拒绝所有足球操作
        if (net.astrorbits.football.match.MatchState.isKickoffInteractionLocked(player)) return
        net.astrorbits.football.match.MatchState.tryNotifyKickoffBallTouched(player)
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

        when (payload.action) {
            FootballActionType.GK_CATCH -> handleCatch(player)
            FootballActionType.GK_DIVE -> handleDive(player, payload)
            FootballActionType.GK_PUNCH -> handlePunch(player)
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
            FootballActionType.GK_DROP,
            -> {
                if (GoalkeeperHoldLock.isReleaseBlocked(player, now)) {
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
                football.releaseHold()
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
                }
            }
            FootballActionType.GK_THROW_LONG -> {
                val sprinting = payload.flags and FootballInputConfig.FLAG_SPRINT != 0
                val chargeSettings = FootballInputConfig.chargeSettings()
                val perfect = KickChargeUtil.isPerfectCharge(payload.chargeHeldMs, chargeSettings)
                val chargeRatio = KickChargeUtil.computeRatio(payload.chargeHeldMs, chargeSettings)
                val params = GoalkeeperUtil.resolveThrowLongParams(chargeRatio, sprinting, perfect)
                football.releaseHold()
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
                }
            }
            FootballActionType.GK_DROP -> {
                if (football.isPlayerBallMovementForbidden(player)) {
                    return
                }
                football.recordActiveKick(player, null)
                football.dropAt(player)
                FootballParticles.playGkCatch(player, football, 0.0)
                FootballSounds.playGkCatch(player, 0.0)
                lastActionTick[player.uuid] = now
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
        if (football.isPlayerBallMovementForbidden(player)) {
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
        val recoil = computeCatchRecoil(speed, incoming)
        applyCatchMomentumDamping(player, player.lookAngle, recoil)
        FootballSounds.playGkCatch(player, speed)
        FootballParticles.playGkCatch(player, football, speed)
        lastActionTick[player.uuid] = now
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
}
