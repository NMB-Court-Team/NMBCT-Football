package net.astrorbits.football.util

import kotlin.math.cos
import kotlin.math.sin
import net.astrorbits.football.Football
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.input.FootballMovementInputUtil
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
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

    fun findNearestFootball(level: Level, center: Vec3, range: Double): Football? {
        val box = AABB.ofSize(center, range * 2.0, range * 2.0, range * 2.0)
        return level.getEntitiesOfClass(Football::class.java, box)
            .filter { it.distanceToSqr(center) <= range * range }
            .minByOrNull { it.distanceToSqr(center) }
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

    fun resolveShootParams(chargeRatio: Float, sprinting: Boolean, perfectCharge: Boolean = false): KickParams {
        val clamped = chargeRatio.coerceIn(0f, 1f)
        var force = FootballInputConfig.SHOOT_FORCE_MIN +
            (FootballInputConfig.SHOOT_FORCE_MAX - FootballInputConfig.SHOOT_FORCE_MIN) * clamped
        if (sprinting && clamped >= FootballInputConfig.SHOOT_MIN_CHARGE_FOR_SPRINT) {
            force *= FootballInputConfig.SHOOT_SPRINT_BONUS
        }
        if (perfectCharge) {
            force *= FootballInputConfig.PERFECT_CHARGE_FORCE_BONUS
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

    fun resolveDribbleDirection(player: Player, dribbleBaseYaw: Float? = null): Vec3 {
        val movement = if (dribbleBaseYaw != null) {
            FootballMovementInputUtil.movementInputVector(player, dribbleBaseYaw)
        } else {
            FootballMovementInputUtil.movementInputVector(player)
        }
        if (movement.lengthSqr() > 1.0e-4) {
            return Vec3Math.normalizeSafe(movement)
        }
        val yaw = dribbleBaseYaw ?: player.yRot
        val yawRad = Math.toRadians(yaw.toDouble())
        return Vec3Math.normalizeSafe(Vec3(-sin(yawRad), 0.0, cos(yawRad)))
    }

    fun applyKickToFootball(player: Player, football: Football, params: KickParams, applySpread: Boolean = false) {
        applyKickToFootballWithLook(
            football = football,
            params = params,
            lookYaw = player.yRot,
            lookPitch = player.xRot,
            random = if (applySpread) player.random else null,
            spreadInaccuracy = if (applySpread) FootballInputConfig.KICK_SPREAD_INACCURACY else 0.0,
        )
    }

    fun applyKickToFootballWithLook(
        football: Football,
        params: KickParams,
        lookYaw: Float,
        lookPitch: Float,
        random: RandomSource? = null,
        spreadInaccuracy: Double = 0.0,
    ) {
        val look = lookDirection(lookYaw, lookPitch)
        val horizontalLook = Vec3Math.horizontal(look)
        val pitchOffset = lookPitchAngleOffset(lookPitch)
        val adjustedParams = params.copy(angleDegrees = params.angleDegrees + pitchOffset)
        applyKickWithHorizontalDirection(football, horizontalLook, look, adjustedParams, random, spreadInaccuracy)
    }

    /** 命令简单踢球：力度 + 仰角，方向由执行朝向水平分量与 elevation 决定。 */
    fun applySimpleCommandKick(
        football: Football,
        power: Double,
        elevation: Double,
        lookYaw: Float,
        lookPitch: Float,
    ) {
        val params = KickParams(force = power, angleDegrees = elevation, heightOffset = 0.0)
        applyCommandKick(football, params, lookYaw, lookPitch)
    }

    /**
     * 由踢球点与目标点计算冲量方向；模长为 [power]。
     * 目标点 [towardPoint] 与 [kickPoint] 重合时返回 null。
     */
    fun buildPreciseKickDirection(
        kickPoint: Vec3,
        towardPoint: Vec3,
        power: Double,
    ): Vec3? {
        val delta = towardPoint.subtract(kickPoint)
        if (delta.lengthSqr() < 1.0e-8) {
            return null
        }
        return Vec3Math.normalizeSafe(delta).scale(power)
    }

    /** 命令精确踢球：直接指定世界坐标踢球点与冲量向量。 */
    fun applyPreciseCommandKick(football: Football, kickPoint: Vec3, direction: Vec3) {
        football.kick(kickPoint, direction)
    }

    /** 命令踢球：使用显式 angle 参数，不叠加视角 pitch 偏移。 */
    fun applyCommandKick(
        football: Football,
        params: KickParams,
        lookYaw: Float,
        lookPitch: Float,
    ) {
        val look = lookDirection(lookYaw, lookPitch)
        val horizontalLook = Vec3Math.horizontal(look)
        applyKickWithHorizontalDirection(football, horizontalLook, look, params)
    }

    /**
     * 将玩家视角 pitch 转为踢球仰角偏移。
     * MC 中 pitch 向上为负、向下为正；仰角偏移与之相反（看上→球抬高）。
     */
    fun lookPitchAngleOffset(lookPitch: Float): Double {
        val raw = -lookPitch.toDouble() * FootballInputConfig.KICK_LOOK_PITCH_INFLUENCE
        return raw.coerceIn(
            FootballInputConfig.KICK_LOOK_PITCH_ANGLE_MIN,
            FootballInputConfig.KICK_LOOK_PITCH_ANGLE_MAX,
        )
    }

    fun lookDirection(lookYaw: Float, lookPitch: Float): Vec3 {
        val pitchRad = Math.toRadians(lookPitch.toDouble())
        val yawRad = Math.toRadians(-lookYaw.toDouble())
        val cosPitch = cos(pitchRad)
        val sinPitch = sin(pitchRad)
        val cosYaw = cos(yawRad)
        val sinYaw = sin(yawRad)
        return Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch)
    }

    fun applyKickWithHorizontalDirection(
        football: Football,
        horizontalLook: Vec3,
        verticalReference: Vec3,
        params: KickParams,
        random: RandomSource? = null,
        spreadInaccuracy: Double = 0.0,
    ) {
        val ballCenter = football.position().add(0.0, FootballPhysicsConfig.RADIUS, 0.0)
        val kickPoint = buildKickPoint(ballCenter, horizontalLook, params.heightOffset)
        var direction = buildKickDirection(horizontalLook, verticalReference, params.force, params.angleDegrees)
        if (random != null && spreadInaccuracy > 0.0) {
            direction = applyProjectileSpread(direction, random, spreadInaccuracy)
        }
        football.kick(kickPoint, direction)
    }

    /**
     * 与原版弹射物 `shoot(..., inaccuracy)` 相同的三轴三角形偏移，归一化后保持冲量模长。
     */
    fun applyProjectileSpread(direction: Vec3, random: RandomSource, inaccuracy: Double): Vec3 {
        val force = direction.length()
        if (force < 1.0e-12 || inaccuracy <= 0.0) {
            return direction
        }
        val unit = direction.normalize()
        val spread = Vec3(
            unit.x + random.triangle(0.0, 0.0172275 * inaccuracy),
            unit.y + random.triangle(0.0, 0.0172275 * inaccuracy),
            unit.z + random.triangle(0.0, 0.0172275 * inaccuracy),
        )
        return Vec3Math.normalizeSafe(spread).scale(force)
    }

    fun applyDribbleTouch(player: Player, football: Football, dribbleBaseYaw: Float? = null) {
        val direction = resolveDribbleDirection(player, dribbleBaseYaw)
        if (direction.lengthSqr() < 1.0e-8) {
            return
        }
        val ballCenter = football.position().add(0.0, FootballPhysicsConfig.RADIUS, 0.0)
        val kickPoint = buildKickPoint(ballCenter, direction, 0.0)
        football.kick(kickPoint, direction.scale(FootballInputConfig.DRIBBLE_TOUCH_FORCE))
    }
}
