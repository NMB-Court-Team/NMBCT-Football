package net.astrorbits.football.input

import net.astrorbits.football.Football
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.FootballParticles
import net.astrorbits.football.network.FootballActionC2SPayload
import net.astrorbits.football.network.FootballActionType
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
    private val lastActionTick = ConcurrentHashMap<UUID, Long>()
    private val diveCooldownUntil = ConcurrentHashMap<UUID, Long>()

    fun handle(player: ServerPlayer, payload: FootballActionC2SPayload) {
        // 服务端双重保险：非发球方球员在开球锁定时拒绝所有足球操作
        if (net.astrorbits.football.match.MatchState.isNonKickoffBlocked(player)) return
        net.astrorbits.football.match.MatchState.notifyKickoffBallTouched(player)
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
            else -> Unit
        }
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
                FootballParticles.playGkThrow(player, football)
                football.releaseHold()
                football.lastKicker = player.uuid
                FootballKickUtil.applyKickToFootballWithLook(
                    football,
                    params,
                    payload.lookYaw,
                    payload.lookPitch,
                    random = player.random,
                    spreadInaccuracy = FootballInputConfig.KICK_SPREAD_INACCURACY,
                )
                FootballSounds.playGkThrow(player)
                lastActionTick[player.uuid] = now
            }
            FootballActionType.GK_THROW_LONG -> {
                val sprinting = payload.flags and FootballInputConfig.FLAG_SPRINT != 0
                val chargeSettings = FootballInputConfig.chargeSettings()
                val perfect = KickChargeUtil.isPerfectCharge(payload.chargeHeldMs, chargeSettings)
                val chargeRatio = KickChargeUtil.computeRatio(payload.chargeHeldMs, chargeSettings)
                val params = GoalkeeperUtil.resolveThrowLongParams(chargeRatio, sprinting, perfect)
                FootballParticles.playGkThrow(player, football)
                football.releaseHold()
                football.lastKicker = player.uuid
                FootballKickUtil.applyKickToFootballWithLook(
                    football,
                    params,
                    payload.lookYaw,
                    payload.lookPitch,
                    random = if (!perfect) player.random else null,
                    spreadInaccuracy = if (!perfect) FootballInputConfig.KICK_SPREAD_INACCURACY else 0.0,
                )
                FootballSounds.playGkThrow(player)
                lastActionTick[player.uuid] = now
            }
            FootballActionType.GK_DROP -> {
                football.lastKicker = player.uuid
                FootballParticles.playGkCatch(player, football, 0.0)
                football.dropAt(player)
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
        if (player.distanceToSqr(football) > range * range) {
            return
        }

        val speed = GoalkeeperUtil.ballSpeed(football)
        if (speed > GoalkeeperInputConfig.GK_CATCH_MAX_SPEED) {
            return
        }
        if (!GoalkeeperUtil.isBallApproachingKeeper(football, player)) {
            return
        }

        football.lastKicker = player.uuid
        football.enterHold(player)
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
        if (player.distanceToSqr(football) > range * range) {
            return
        }

        FootballKickUtil.applyKickToFootball(player, football, GoalkeeperUtil.resolvePunchParams())
        FootballSounds.playGkPunch(player)
        FootballParticles.playGkPunch(player, football)
        lastActionTick[player.uuid] = now
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
        val speed = GoalkeeperUtil.ballSpeed(football)
        football.lastKicker = player.uuid
        val incoming = football.getPhysicsState().linearVelocity
        football.enterHold(player)

        val clampedRecoil = if (speed < GoalkeeperInputConfig.GK_DIVE_CATCH_RECOIL_MIN_SPEED) {
            Vec3.ZERO
        } else {
            val recoilImpulse = incoming.scale(GoalkeeperInputConfig.GK_DIVE_DEFLECT_FORCE_SCALE * 0.2)
            val recoilLength = recoilImpulse.length()
            val maxRecoil = 0.75
            if (recoilLength > maxRecoil && recoilLength > 1.0e-8) {
                recoilImpulse.scale(maxRecoil / recoilLength)
            } else {
                recoilImpulse
            }
        }
        applyDiveMomentumDamping(player, diveDirection, clampedRecoil)

        FootballSounds.playGkCatch(player, speed)
        FootballParticles.playGkCatch(player, football, speed)
        return true
    }

    private fun applyDiveMomentumDamping(player: ServerPlayer, diveDirection: Vec3, recoil: Vec3) {
        val horizontalDir = Vec3Math.normalizeSafe(Vec3(diveDirection.x, 0.0, diveDirection.z))
        val current = player.deltaMovement
        val forwardComponent = if (horizontalDir.lengthSqr() > 1.0e-8) {
            horizontalDir.scale(current.dot(horizontalDir))
        } else {
            Vec3.ZERO
        }
        val remaining = current.subtract(forwardComponent)
        val dampedForward = forwardComponent.scale(0.15)
        player.setDeltaMovement(remaining.add(dampedForward).add(recoil))
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
