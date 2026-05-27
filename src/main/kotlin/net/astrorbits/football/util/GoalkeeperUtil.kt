package net.astrorbits.football.util

import net.astrorbits.football.Football
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

object GoalkeeperUtil {
    fun catchRange(player: ServerPlayer): Double {
        var range = GoalkeeperInputConfig.GK_CATCH_RANGE
        if (player.isShiftKeyDown) {
            range += GoalkeeperInputConfig.GK_CROUCH_RANGE_BONUS
        }
        return range
    }

    fun diveRange(player: ServerPlayer): Double {
        var range = GoalkeeperInputConfig.GK_DIVE_RANGE
        if (player.isShiftKeyDown) {
            range += GoalkeeperInputConfig.GK_CROUCH_RANGE_BONUS
        }
        return range
    }

    fun punchRange(player: ServerPlayer): Double {
        var range = GoalkeeperInputConfig.GK_PUNCH_RANGE
        if (player.isShiftKeyDown) {
            range += GoalkeeperInputConfig.GK_CROUCH_RANGE_BONUS
        }
        return range
    }

    fun findHeldFootball(player: ServerPlayer): Football? {
        return player.level().getEntitiesOfClass(
            Football::class.java,
            player.boundingBox.inflate(6.0),
        ).firstOrNull { it.isHeldBy(player) }
    }

    fun ballSpeed(football: Football): Double = football.getPhysicsState().linearVelocity.length()

    fun ballCenter(football: Football): Vec3 =
        football.position().add(0.0, FootballPhysicsConfig.RADIUS, 0.0)

    fun isBallApproachingKeeper(football: Football, player: ServerPlayer): Boolean {
        val velocity = football.getPhysicsState().linearVelocity
        if (velocity.lengthSqr() < 1.0e-6) {
            return true
        }
        val toKeeper = player.position().add(0.0, player.eyeHeight * 0.5, 0.0).subtract(ballCenter(football))
        if (toKeeper.lengthSqr() < 1.0e-6) {
            return true
        }
        val dot = velocity.normalize().dot(toKeeper.normalize())
        val minDot = cos(Math.toRadians(GoalkeeperInputConfig.GK_CATCH_ANGLE_DEG / 2.0))
        return dot >= minDot
    }

    fun isInDirectionalSector(
        origin: Vec3,
        direction: Vec3,
        target: Vec3,
        range: Double,
        halfAngleDeg: Double,
    ): Boolean {
        val offset = target.subtract(origin)
        val horizontal = Vec3Math.horizontal(offset)
        if (horizontal.lengthSqr() < 1.0e-8 || offset.lengthSqr() > range * range) {
            return false
        }
        val dir = Vec3Math.normalizeSafe(Vec3Math.horizontal(direction))
        if (dir.lengthSqr() < 1.0e-8) {
            return false
        }
        val dot = dir.dot(Vec3Math.normalizeSafe(horizontal))
        val minDot = cos(Math.toRadians(halfAngleDeg))
        return dot >= minDot
    }

    fun resolveDiveDirection(player: ServerPlayer, useLookOnly: Boolean): Vec3 {
        if (!useLookOnly) {
            val movement = net.astrorbits.football.input.FootballMovementInputUtil.movementInputVector(player)
            if (movement.lengthSqr() > 1.0e-4) {
                return Vec3Math.normalizeSafe(movement)
            }
        }
        return Vec3Math.normalizeSafe(Vec3Math.horizontal(player.lookAngle))
    }

    fun resolveThrowLongParams(chargeRatio: Float, sprinting: Boolean): KickParams {
        val clamped = chargeRatio.coerceIn(0f, 1f)
        var force = GoalkeeperInputConfig.GK_THROW_LONG_FORCE_MIN +
            (GoalkeeperInputConfig.GK_THROW_LONG_FORCE_MAX - GoalkeeperInputConfig.GK_THROW_LONG_FORCE_MIN) * clamped
        if (sprinting) {
            force *= GoalkeeperInputConfig.GK_THROW_SPRINT_BONUS
        }
        val angle = GoalkeeperInputConfig.GK_THROW_LONG_ANGLE_MIN_DEG +
            (GoalkeeperInputConfig.GK_THROW_LONG_ANGLE_MAX_DEG - GoalkeeperInputConfig.GK_THROW_LONG_ANGLE_MIN_DEG) * clamped
        return KickParams(force = force, angleDegrees = angle, heightOffset = 0.0)
    }

    fun resolveThrowShortParams(): KickParams = KickParams(
        force = GoalkeeperInputConfig.GK_THROW_SHORT_FORCE,
        angleDegrees = 0.0,
        heightOffset = 0.0,
    )

    fun resolvePunchParams(): KickParams = KickParams(
        force = GoalkeeperInputConfig.GK_PUNCH_FORCE,
        angleDegrees = 5.0,
        heightOffset = 0.0,
    )

    private fun cos(radians: Double): Double = kotlin.math.cos(radians)
}
