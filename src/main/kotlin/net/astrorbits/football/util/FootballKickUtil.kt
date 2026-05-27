package net.astrorbits.football.util

import kotlin.math.cos
import kotlin.math.sin
import net.astrorbits.football.Football
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.input.FootballMovementInputUtil
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

data class KickParams(
    val force: Double,
    val angleDegrees: Double,
    val heightOffset: Double
)

object FootballKickUtil {
    fun findNearestFootball(player: Player, range: Double): Football? {
        val box = player.boundingBox.inflate(range)
        return player.level().getEntitiesOfClass(Football::class.java, box)
            .minByOrNull { it.distanceToSqr(player) }
    }

    fun buildKickDirection(
        horizontalLook: Vec3,
        look: Vec3,
        force: Double,
        angleDegrees: Double
    ): Vec3 {
        if (horizontalLook.lengthSqr() < 1.0e-8) {
            return look.scale(force)
        }

        val horizontalUnit = Vec3Math.normalizeSafe(horizontalLook)
        val pitchRad = Math.toRadians(angleDegrees)
        val unitDirection = horizontalUnit.scale(cos(pitchRad)).add(0.0, sin(pitchRad), 0.0)
        return unitDirection.scale(force)
    }

    fun buildKickPoint(ballCenter: Vec3, horizontalLook: Vec3, heightOffset: Double): Vec3 {
        val horizontalOffset = if (horizontalLook.lengthSqr() > 1.0e-8) {
            Vec3Math.normalizeSafe(horizontalLook).scale(-FootballPhysicsConfig.RADIUS)
        } else {
            Vec3.ZERO
        }
        return ballCenter.add(horizontalOffset.x, heightOffset, horizontalOffset.z)
    }

    fun resolvePassParams(): KickParams = KickParams(
        force = FootballInputConfig.PASS_FORCE,
        angleDegrees = 0.0,
        heightOffset = 0.0
    )

    fun resolveShootParams(chargeRatio: Float, sprinting: Boolean): KickParams {
        val clamped = chargeRatio.coerceIn(0f, 1f)
        var force = FootballInputConfig.SHOOT_FORCE_MIN +
            (FootballInputConfig.SHOOT_FORCE_MAX - FootballInputConfig.SHOOT_FORCE_MIN) * clamped
        if (sprinting && clamped >= FootballInputConfig.SHOOT_MIN_CHARGE_FOR_SPRINT) {
            force *= FootballInputConfig.SHOOT_SPRINT_BONUS
        }
        val angle = FootballInputConfig.SHOOT_ANGLE_MIN_DEG +
            (FootballInputConfig.SHOOT_ANGLE_MAX_DEG - FootballInputConfig.SHOOT_ANGLE_MIN_DEG) * clamped
        return KickParams(force = force, angleDegrees = angle, heightOffset = 0.0)
    }

    fun resolveChipParams(player: Player): KickParams {
        val look = player.lookAngle
        val pitchUp = look.y.coerceAtLeast(0.0)
        val extraAngle = (pitchUp * 40.0).coerceAtMost(FootballInputConfig.CHIP_ANGLE_EXTRA_MAX)
        return KickParams(
            force = FootballInputConfig.CHIP_FORCE,
            angleDegrees = FootballInputConfig.CHIP_ANGLE_DEG + extraAngle,
            heightOffset = FootballInputConfig.CHIP_HEIGHT_OFFSET
        )
    }

    fun resolveDribbleDirection(player: Player): Vec3 {
        val movement = FootballMovementInputUtil.movementInputVector(player)
        if (movement.lengthSqr() > 1.0e-4) {
            return Vec3Math.normalizeSafe(movement)
        }
        return Vec3Math.normalizeSafe(Vec3Math.horizontal(player.lookAngle))
    }

    fun applyKickToFootball(player: Player, football: Football, params: KickParams) {
        val look = player.lookAngle
        val horizontalLook = Vec3Math.horizontal(look)
        val ballCenter = football.position().add(0.0, FootballPhysicsConfig.RADIUS, 0.0)
        val kickPoint = buildKickPoint(ballCenter, horizontalLook, params.heightOffset)
        val direction = buildKickDirection(horizontalLook, look, params.force, params.angleDegrees)
        football.kick(kickPoint, direction)
    }

    fun applyDribbleToFootball(player: Player, football: Football) {
        val direction = resolveDribbleDirection(player)
        if (direction.lengthSqr() < 1.0e-8) {
            return
        }
        val ballCenter = football.position().add(0.0, FootballPhysicsConfig.RADIUS, 0.0)
        val kickPoint = buildKickPoint(ballCenter, direction, 0.0)
        football.kick(kickPoint, direction.scale(FootballInputConfig.DRIBBLE_FORCE))
    }
}
