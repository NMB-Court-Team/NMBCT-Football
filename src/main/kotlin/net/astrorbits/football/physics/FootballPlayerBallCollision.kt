package net.astrorbits.football.physics

import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.util.Vec3Math
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * 球心（半径 [FootballPhysicsConfig.RADIUS]）与玩家 AABB 的扫掠/重叠检测，
 * 以及基于质量与恢复系数的动量交换。
 */
object FootballPlayerBallCollision {
    data class SweepHit(
        val t: Double,
        val normal: Vec3,
        val contactCenter: Vec3,
    )

    data class MomentumResult(
        val ballVelocity: Vec3,
        val playerDelta: Vec3,
        val impulseMagnitude: Double,
    )

    private const val CONTACT_SKIN = 0.015
    private const val SWEEP_MAX_STEP = 0.45
    private const val PUSH_DEFLECTION_BIAS = 0.62
    private const val PUSH_DEFLECTION_DEADZONE = 0.02
    /** 重叠且几何法线不可靠时，沿推球轴的最低速度耦合。 */
    private const val QUASI_STATIC_OVERLAP_COUPLING = 0.35

    fun horizontalContactNormal(contactNormal: Vec3): Vec3 {
        return Vec3Math.normalizeSafe(Vec3(contactNormal.x, 0.0, contactNormal.z))
    }

    /** 准静态推球轴：水平接触法线 + 擦角偏转（唯一擦角模型，不再单独做切向摩擦叠加）。 */
    fun bodyPushAxis(contactNormal: Vec3, playerHorizontal: Vec3): Vec3 {
        return deflectBodyPushDirection(horizontalContactNormal(contactNormal), playerHorizontal)
    }

    /**
     * 水平接触法线 + 相对移动方向的擦角偏转。
     */
    fun deflectBodyPushDirection(
        contactNormalHorizontal: Vec3,
        playerHorizontal: Vec3,
    ): Vec3 {
        val moveDir = Vec3Math.normalizeSafe(playerHorizontal)
        val baseDir = Vec3Math.normalizeSafe(contactNormalHorizontal, moveDir)
        if (moveDir.lengthSqr() <= 1.0e-8 || baseDir.lengthSqr() <= 1.0e-8) {
            return baseDir
        }

        val lateral = Vec3(-moveDir.z, 0.0, moveDir.x)
        val side = baseDir.dot(lateral)
        if (abs(side) <= PUSH_DEFLECTION_DEADZONE) {
            return baseDir
        }

        val glancing = (1.0 - baseDir.dot(moveDir).coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0)
        val bias = PUSH_DEFLECTION_BIAS * (0.45 + glancing * 0.55)
        return Vec3Math.normalizeSafe(
            baseDir.add(lateral.scale(sign(side) * bias)),
            baseDir,
        )
    }

    fun playerMotionEnvelope(playerBox: AABB, playerStep: Vec3): AABB {
        if (playerStep.lengthSqr() <= 1.0e-12) {
            return playerBox
        }
        val boxAtStepStart = playerBox.move(-playerStep.x, -playerStep.y, -playerStep.z)
        return boxAtStepStart.minmax(playerBox)
    }
    /**
     * 球心相对玩家 AABB（未 inflate）的最近点与外向法线（玩家表面 → 球心）。
     * 角/边撞击时法线为各轴混合，而非 slab 的纯 ±X/±Y/±Z。
     */
    fun contactAtSphereCenter(
        sphereCenter: Vec3,
        playerBox: AABB,
        radius: Double,
    ): SweepHit? {
        val closestX = sphereCenter.x.coerceIn(playerBox.minX, playerBox.maxX)
        val closestY = sphereCenter.y.coerceIn(playerBox.minY, playerBox.maxY)
        val closestZ = sphereCenter.z.coerceIn(playerBox.minZ, playerBox.maxZ)
        val closest = Vec3(closestX, closestY, closestZ)
        val delta = sphereCenter.subtract(closest)
        val deltaLenSqr = delta.lengthSqr()
        val epsilon = FootballPhysicsConfig.EPSILON

        if (deltaLenSqr > epsilon * epsilon) {
            val normal = Vec3Math.normalizeSafe(delta)
            val contactCenter = closest.add(normal.scale(radius + CONTACT_SKIN))
            return SweepHit(t = 0.0, normal = normal, contactCenter = contactCenter)
        }

        val push = minimumSeparationPush(sphereCenter, playerBox.inflate(radius)) ?: return null
        val normal = Vec3Math.normalizeSafe(push)
        return SweepHit(
            t = 0.0,
            normal = normal,
            contactCenter = sphereCenter,
        )
    }

    /**
     * 球心扫掠射线检测（分段）；[playerBox] 为未 inflate 的玩家盒，用于几何法线。
     */
    fun sweepBallCenter(
        start: Vec3,
        end: Vec3,
        playerBox: AABB,
        radius: Double,
    ): SweepHit? {
        val collisionBox = playerBox.inflate(radius)
        val segment = end.subtract(start)
        val length = segment.length()
        if (length <= 1.0e-12) {
            return overlapHitAt(start, playerBox, radius)
        }

        val steps = max(1, ceil(length / SWEEP_MAX_STEP).toInt())
        var segStart = start
        for (step in 1..steps) {
            val fraction = step.toDouble() / steps
            val segEnd = start.add(segment.scale(fraction))
            val localHit = segmentAabbHit(segStart, segEnd, collisionBox) ?: run {
                segStart = segEnd
                continue
            }
            val globalT = ((step - 1) + localHit.t) / steps
            val hitCenter = start.add(segment.scale(globalT))
            val contact = contactAtSphereCenter(hitCenter, playerBox, radius)
                ?: SweepHit(
                    t = globalT,
                    normal = localHit.normal,
                    contactCenter = hitCenter.add(localHit.normal.scale(CONTACT_SKIN)),
                )
            return contact.copy(t = globalT)
        }
        return null
    }

    /** 球与玩家碰撞体重叠时，用法线来自 [contactAtSphereCenter]。 */
    fun overlapHitAt(center: Vec3, playerBox: AABB, radius: Double): SweepHit? {
        if (minimumSeparationPush(center, playerBox.inflate(radius)) == null) {
            return null
        }
        return contactAtSphereCenter(center, playerBox, radius)
    }

    /**
     * 沿法线 [normal]（玩家 → 球）的 1D 动量交换；[normal] 不必已归一化。
     * 仅当沿法线相对速度为接近（closing）时施加冲量；切向速度保留。
     */
    fun resolveMomentum(
        ballVelocity: Vec3,
        playerVelocity: Vec3,
        normal: Vec3,
        ballMass: Double = FootballPhysicsConfig.MASS,
        playerMass: Double = FootballInputConfig.PLAYER_MASS,
        restitution: Double = FootballInputConfig.BALL_PLAYER_RESTITUTION,
    ): MomentumResult? {
        val n = Vec3Math.normalizeSafe(normal)
        if (n.lengthSqr() <= FootballPhysicsConfig.EPSILON * FootballPhysicsConfig.EPSILON) {
            return null
        }

        val relativeNormal = ballVelocity.subtract(playerVelocity).dot(n)
        if (relativeNormal >= -FootballPhysicsConfig.EPSILON) {
            return null
        }

        val invMassSum = 1.0 / ballMass + 1.0 / playerMass
        val impulse = -(1.0 + restitution.coerceIn(0.0, 1.25)) * relativeNormal / invMassSum
        if (impulse <= FootballPhysicsConfig.EPSILON) {
            return null
        }

        val newBallVelocity = ballVelocity.add(n.scale(impulse / ballMass))
        val playerDelta = n.scale(-impulse / playerMass)
        return MomentumResult(
            ballVelocity = newBallVelocity,
            playerDelta = playerDelta,
            impulseMagnitude = impulse,
        )
    }

    /**
     * 动量交换未触发时：沿 [bodyPushAxis] 分解玩家速度并施加 capped 推力；重叠时保留最低耦合。
     */
    fun resolveQuasiStaticPush(
        ballVelocity: Vec3,
        playerVelocity: Vec3,
        normal: Vec3,
        ballMass: Double = FootballPhysicsConfig.MASS,
        playerMass: Double = FootballInputConfig.PLAYER_MASS,
        pushScale: Double = FootballInputConfig.PLAYER_BALL_PUSH_SCALE,
        pushMax: Double = FootballInputConfig.PLAYER_BALL_PUSH_MAX,
        minPlayerSpeed: Double = FootballInputConfig.PLAYER_BALL_PUSH_MIN_SPEED,
    ): MomentumResult? {
        val playerHorizontal = Vec3Math.horizontal(playerVelocity)
        val playerSpeed = playerHorizontal.length()
        if (playerSpeed < minPlayerSpeed) {
            return null
        }

        val deltaV = computeQuasiStaticDeltaVelocity(
            playerHorizontal = playerHorizontal,
            contactNormal = normal,
            playerSpeed = playerSpeed,
            pushScale = pushScale,
            pushMax = pushMax,
            minPlayerSpeed = minPlayerSpeed,
        ) ?: return null

        val newBallVelocity = ballVelocity.add(deltaV)
        val impulse = deltaV.length() * ballMass
        val playerDelta = Vec3Math.normalizeSafe(deltaV).scale(-impulse / playerMass)
        return MomentumResult(
            ballVelocity = newBallVelocity,
            playerDelta = playerDelta,
            impulseMagnitude = impulse,
        )
    }

    internal fun computeQuasiStaticDeltaVelocity(
        playerHorizontal: Vec3,
        contactNormal: Vec3,
        playerSpeed: Double,
        pushScale: Double,
        pushMax: Double,
        minPlayerSpeed: Double,
    ): Vec3? {
        val eps = FootballPhysicsConfig.EPSILON
        val scale = pushScale.coerceAtLeast(0.0)
        val cap = pushMax.coerceAtLeast(0.0)

        val pushAxis = bodyPushAxis(contactNormal, playerHorizontal)
        if (pushAxis.lengthSqr() <= eps * eps) {
            return null
        }

        val decomp = Vec3Math.decomposePlanar(playerHorizontal, pushAxis)
        var approachSpeed = decomp.normalComponent.coerceAtLeast(0.0)

        if (approachSpeed < minPlayerSpeed) {
            val moveDir = Vec3Math.normalizeSafe(playerHorizontal)
            val coupling = pushAxis.dot(moveDir).coerceAtLeast(QUASI_STATIC_OVERLAP_COUPLING)
            val fallbackApproach = playerSpeed * coupling
            if (fallbackApproach < minPlayerSpeed) {
                return null
            }
            approachSpeed = fallbackApproach
        }

        val imparted = min(approachSpeed * scale, cap)
        if (imparted <= eps) {
            return null
        }
        return pushAxis.scale(imparted)
    }

    /**
     * 球撞玩家时的可感知水平后坐力（与动量交换解耦，按文档 `ball_player_push_scale` / `ball_player_max_push`）。
     * [normal] 为玩家 → 球；仅当沿法线球相对玩家接近（`relativeNormal < 0`）时生效。
     */
    fun resolvePlayerRecoil(
        ballVelocity: Vec3,
        playerVelocity: Vec3,
        normal: Vec3,
        recoilMinSpeed: Double = FootballInputConfig.BALL_PLAYER_RECOIL_MIN_SPEED,
        pushScale: Double = FootballInputConfig.BALL_PLAYER_PUSH_SCALE,
        pushMax: Double = FootballInputConfig.BALL_PLAYER_MAX_PUSH,
    ): Vec3 {
        val n = Vec3Math.normalizeSafe(normal)
        if (n.lengthSqr() <= FootballPhysicsConfig.EPSILON * FootballPhysicsConfig.EPSILON) {
            return Vec3.ZERO
        }

        val relativeNormal = ballVelocity.subtract(playerVelocity).dot(n)
        if (relativeNormal >= -FootballPhysicsConfig.EPSILON) {
            return Vec3.ZERO
        }

        val closingSpeed = -relativeNormal
        if (closingSpeed < recoilMinSpeed) {
            return Vec3.ZERO
        }

        val recoilSpeed = min(closingSpeed * pushScale.coerceAtLeast(0.0), pushMax.coerceAtLeast(0.0))
        if (recoilSpeed <= FootballPhysicsConfig.EPSILON) {
            return Vec3.ZERO
        }

        val horizontalNormal = Vec3Math.normalizeSafe(Vec3(n.x, 0.0, n.z))
        if (horizontalNormal.lengthSqr() <= FootballPhysicsConfig.EPSILON * FootballPhysicsConfig.EPSILON) {
            return Vec3.ZERO
        }
        return horizontalNormal.scale(-recoilSpeed)
    }

    fun capPlayerKnockback(playerDelta: Vec3, maxHorizontalSpeed: Double): Vec3 {
        val horizontal = Vec3Math.horizontal(playerDelta)
        val maxSpeed = maxHorizontalSpeed.coerceAtLeast(0.0)
        if (maxSpeed <= 0.0 || horizontal.lengthSqr() <= maxSpeed * maxSpeed) {
            return playerDelta
        }
        val scaled = Vec3Math.normalizeSafe(horizontal).scale(maxSpeed)
        return Vec3(scaled.x, playerDelta.y, scaled.z)
    }

    private data class SegmentAabbHit(val t: Double, val normal: Vec3)

    private fun minimumSeparationPush(center: Vec3, box: AABB): Vec3? {
        if (center.x < box.minX || center.x > box.maxX ||
            center.y < box.minY || center.y > box.maxY ||
            center.z < box.minZ || center.z > box.maxZ
        ) {
            return null
        }

        val pushLeft = box.minX - center.x - CONTACT_SKIN
        val pushRight = box.maxX - center.x + CONTACT_SKIN
        val pushDown = box.minY - center.y - CONTACT_SKIN
        val pushUp = box.maxY - center.y + CONTACT_SKIN
        val pushBack = box.minZ - center.z - CONTACT_SKIN
        val pushFront = box.maxZ - center.z + CONTACT_SKIN
        val candidates = arrayOf(
            Vec3(pushLeft, 0.0, 0.0),
            Vec3(pushRight, 0.0, 0.0),
            Vec3(0.0, pushDown, 0.0),
            Vec3(0.0, pushUp, 0.0),
            Vec3(0.0, 0.0, pushBack),
            Vec3(0.0, 0.0, pushFront),
        )

        var bestPush: Vec3? = null
        var bestLenSqr = Double.MAX_VALUE
        for (candidate in candidates) {
            val lenSqr = candidate.lengthSqr()
            if (lenSqr in 1.0e-12..<bestLenSqr) {
                bestLenSqr = lenSqr
                bestPush = candidate
            }
        }
        return bestPush
    }

    private fun segmentAabbHit(start: Vec3, end: Vec3, box: AABB): SegmentAabbHit? {
        val direction = end.subtract(start)
        var tMin = 0.0
        var tMax = 1.0
        var enterNormal = Vec3.ZERO

        fun updateAxis(
            startValue: Double,
            dirValue: Double,
            minBound: Double,
            maxBound: Double,
            negativeNormal: Vec3,
            positiveNormal: Vec3,
        ): Boolean {
            if (abs(dirValue) < 1.0e-9) {
                return startValue in minBound..maxBound
            }

            val t1 = (minBound - startValue) / dirValue
            val t2 = (maxBound - startValue) / dirValue
            val axisEnterT: Double
            val axisExitT: Double
            val axisEnterNormal: Vec3

            if (t1 <= t2) {
                axisEnterT = t1
                axisExitT = t2
                axisEnterNormal = negativeNormal
            } else {
                axisEnterT = t2
                axisExitT = t1
                axisEnterNormal = positiveNormal
            }

            if (axisEnterT > tMin) {
                tMin = axisEnterT
                enterNormal = axisEnterNormal
            }
            tMax = min(tMax, axisExitT)
            return tMin <= tMax
        }

        if (!updateAxis(start.x, direction.x, box.minX, box.maxX, Vec3(-1.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0))) {
            return null
        }
        if (!updateAxis(start.y, direction.y, box.minY, box.maxY, Vec3(0.0, -1.0, 0.0), Vec3(0.0, 1.0, 0.0))) {
            return null
        }
        if (!updateAxis(start.z, direction.z, box.minZ, box.maxZ, Vec3(0.0, 0.0, -1.0), Vec3(0.0, 0.0, 1.0))) {
            return null
        }
        if (tMax < 0.0 || tMin > 1.0) {
            return null
        }
        return SegmentAabbHit(t = tMin.coerceIn(0.0, 1.0), normal = enterNormal)
    }
}
