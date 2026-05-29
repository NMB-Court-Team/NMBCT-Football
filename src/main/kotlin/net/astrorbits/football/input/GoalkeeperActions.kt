package net.astrorbits.football.input

import net.astrorbits.football.Football
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.FootballParticles
import net.astrorbits.football.network.FootballActionC2SPayload
import net.astrorbits.football.network.FootballActionType
import net.astrorbits.football.util.FootballKickUtil
import net.astrorbits.football.util.GoalkeeperUtil
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
                FootballParticles.playGkThrow(player, football)
                football.releaseHold()
                FootballKickUtil.applyKickToFootballWithLook(
                    football,
                    GoalkeeperUtil.resolveThrowShortParams(),
                    payload.lookYaw,
                    payload.lookPitch,
                )
                FootballSounds.playGkThrow(player)
                lastActionTick[player.uuid] = now
            }
            FootballActionType.GK_THROW_LONG -> {
                val sprinting = payload.flags and FootballInputConfig.FLAG_SPRINT != 0
                FootballParticles.playGkThrow(player, football)
                football.releaseHold()
                FootballKickUtil.applyKickToFootballWithLook(
                    football,
                    GoalkeeperUtil.resolveThrowLongParams(payload.chargeRatio, sprinting),
                    payload.lookYaw,
                    payload.lookPitch,
                )
                FootballSounds.playGkThrow(player)
                lastActionTick[player.uuid] = now
            }
            FootballActionType.GK_DROP -> {
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

        val useLookOnly = payload.flags and FootballInputConfig.FLAG_DIVE_USE_LOOK != 0
        val direction = GoalkeeperUtil.resolveDiveDirection(player, useLookOnly)
        if (direction.lengthSqr() < 1.0e-8) {
            return
        }

        GoalkeeperDiveSessions.begin(player, direction, now)
        FootballSounds.playGkDive(player)
        FootballParticles.playGkDive(player)
        diveCooldownUntil[player.uuid] = now + GoalkeeperInputConfig.GK_DIVE_COOLDOWN_TICKS
    }

    fun tryResolveDiveCatch(player: ServerPlayer, football: Football, diveDirection: Vec3): Boolean {
        val speed = GoalkeeperUtil.ballSpeed(football)
        if (speed <= GoalkeeperInputConfig.GK_DIVE_CATCH_MAX_SPEED) {
            football.enterHold(player)
            FootballSounds.playGkCatch(player, speed)
            FootballParticles.playGkCatch(player, football, speed)
            return true
        }

        val deflectDir = applyDeflectDirection(diveDirection, player)
        val force = speed * GoalkeeperInputConfig.GK_DIVE_DEFLECT_FORCE_SCALE
        FootballKickUtil.applyKickWithHorizontalDirection(
            football,
            deflectDir,
            deflectDir.add(0.0, 0.15, 0.0),
            net.astrorbits.football.util.KickParams(force = force, angleDegrees = 8.0, heightOffset = 0.0),
        )
        FootballSounds.playGkPunch(player)
        FootballParticles.playGkPunch(player, football)
        return true
    }

    private fun applyDeflectDirection(diveDirection: Vec3, player: ServerPlayer): Vec3 {
        val spread = (player.random.nextDouble() - 0.5) * 30.0
        val yawRad = Math.toRadians(spread)
        val x = diveDirection.x * kotlin.math.cos(yawRad) - diveDirection.z * kotlin.math.sin(yawRad)
        val z = diveDirection.x * kotlin.math.sin(yawRad) + diveDirection.z * kotlin.math.cos(yawRad)
        return Vec3Math.normalizeSafe(Vec3(x, 0.0, z))
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
