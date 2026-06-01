package net.astrorbits.football.util

import net.astrorbits.football.Football
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.astrorbits.football.input.FootballInputConfig
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

    fun standingCatchOrigin(player: ServerPlayer): Vec3 {
        val base = player.position()
        if (player.isShiftKeyDown) {
            return base.add(0.0, 0.55, 0.0)
        }
        return base.add(0.0, player.eyeHeight * 0.5, 0.0)
    }

    fun standingCatchDistanceSqr(player: ServerPlayer, football: Football): Double {
        return standingCatchOrigin(player).distanceToSqr(ballCenter(football))
    }

    fun canStandingCatchBall(football: Football, player: ServerPlayer): Boolean {
        val velocity = football.getPhysicsState().linearVelocity
        if (velocity.lengthSqr() < 1.0e-6) {
            return true
        }

        val ballPos = ballCenter(football)
        val catchOrigin = standingCatchOrigin(player)
        val toKeeper = catchOrigin.subtract(ballPos)
        if (toKeeper.lengthSqr() < 1.0e-6) {
            return true
        }

        val minDot = cos(Math.toRadians(GoalkeeperInputConfig.GK_CATCH_ANGLE_DEG / 2.0))
        val lowBallThreshold = catchOrigin.y - ballPos.y
        if (lowBallThreshold > 0.35) {
            val horizontalVelocity = Vec3Math.horizontal(velocity)
            val horizontalToKeeper = Vec3Math.horizontal(toKeeper)
            if (horizontalVelocity.lengthSqr() < 1.0e-6) {
                return true
            }
            if (horizontalToKeeper.lengthSqr() < 1.0e-8) {
                return true
            }
            val dot = horizontalVelocity.normalize().dot(Vec3Math.normalizeSafe(horizontalToKeeper))
            return dot >= minDot
        }

        val dot = velocity.normalize().dot(Vec3Math.normalizeSafe(toKeeper))
        return dot >= minDot
    }

    fun isBallApproachingKeeper(football: Football, player: ServerPlayer): Boolean =
        canStandingCatchBall(football, player)

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

    /**
     * 鱼跃冲量随视角俯仰（Minecraft `xRot`：负=仰视，正=俯视）的缩放。
     * - 仰视：跳得更高、扑得更近
     * - 平视→俯视 30°：高度与距离同步降低
     * - 俯视 >30°：贴地前扑，距离为最短档且不再缩短
     */
    data class DivePitchScalars(
        val heightScale: Double,
        val forwardScale: Double,
        val groundedDive: Boolean,
        val groundVerticalSpeed: Double,
    )

    fun resolveDivePitchScalars(lookPitch: Float): DivePitchScalars {
        val cfg = GoalkeeperInputConfig.GK_DIVE_PITCH
        val pitch = lookPitch.toDouble()
        val groundThreshold = cfg.groundPitchThresholdDeg.coerceAtLeast(1.0e-6)
        if (pitch > groundThreshold) {
            return DivePitchScalars(
                heightScale = cfg.groundHeightScale,
                forwardScale = cfg.groundForwardScale,
                groundedDive = true,
                groundVerticalSpeed = cfg.groundVerticalSpeed,
            )
        }
        if (pitch >= 0.0) {
            val t = (pitch / groundThreshold).coerceIn(0.0, 1.0)
            return DivePitchScalars(
                heightScale = lerp(1.0, cfg.groundHeightScale, t),
                forwardScale = lerp(1.0, cfg.groundForwardScale, t),
                groundedDive = false,
                groundVerticalSpeed = cfg.groundVerticalSpeed,
            )
        }
        val lookUpRef = cfg.lookUpReferencePitchDeg.coerceAtLeast(1.0e-6)
        val t = (-pitch / lookUpRef).coerceIn(0.0, 1.0)
        return DivePitchScalars(
            heightScale = lerp(1.0, cfg.lookUpMaxHeightScale, t),
            forwardScale = lerp(1.0, cfg.lookUpMinForwardScale, t),
            groundedDive = false,
            groundVerticalSpeed = cfg.groundVerticalSpeed,
        )
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t.coerceIn(0.0, 1.0)

    fun resolveDiveLookDirection(lookYaw: Float, lookPitch: Float): Vec3 {
        val look = Vec3.directionFromRotation(lookPitch, lookYaw)
        val horizontal = Vec3Math.horizontal(look)
        if (horizontal.lengthSqr() > 1.0e-8) {
            return Vec3Math.normalizeSafe(horizontal)
        }
        return Vec3Math.normalizeSafe(Vec3Math.horizontal(Vec3.directionFromRotation(0f, lookYaw)))
    }

    fun resolveDiveChargeRatio(chargeHeldMs: Long, chargeRatio: Float): Float {
        if (chargeHeldMs > 0L) {
            val settings = FootballInputConfig.chargeSettings()
            return KickChargeUtil.computeLinearRatio(chargeHeldMs, settings)
        }
        return chargeRatio.coerceIn(0f, 1f)
    }

    fun resolveThrowLongParams(chargeRatio: Float, sprinting: Boolean, perfectCharge: Boolean = false): KickParams {
        val clamped = chargeRatio.coerceIn(0f, 1f)
        var force = GoalkeeperInputConfig.GK_THROW_LONG_FORCE_MIN +
            (GoalkeeperInputConfig.GK_THROW_LONG_FORCE_MAX - GoalkeeperInputConfig.GK_THROW_LONG_FORCE_MIN) * clamped
        if (sprinting) {
            force *= GoalkeeperInputConfig.GK_THROW_SPRINT_BONUS
        }
        if (perfectCharge) {
            force *= FootballInputConfig.PERFECT_CHARGE_FORCE_BONUS
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
