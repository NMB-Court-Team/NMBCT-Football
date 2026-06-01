package net.astrorbits.football.physics

import net.astrorbits.football.entity.GoalNetEntity
import net.astrorbits.football.util.FootballBlockDepenetration
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

/**
 * 足球与实体化球网的动量交换：
 * - 球撞网时法向动量被大幅吸收（不反弹），切向保留以“贴网下滑”。
 * - 被吸收的动量转化为对附近网格节点的位移，形成网面被顶出的凹陷。
 */
object FootballNetInteraction {
    private const val EPS = 1.0e-6
    private const val STRETCH_CROSSED_BONUS = 0.95

    /**
     * 在足球完成本 tick 位移后调用。
     *
     * @param prevCenter 本 tick 起始的球心
     * @param currCenter 本 tick 结束的球心
     * @return 是否与某张网发生了接触
     */
    fun apply(level: Level, state: net.astrorbits.football.physics.FootballPhysicsState,
              prevCenter: Vec3, currCenter: Vec3): NetContact? {
        val radius = FootballPhysicsConfig.RADIUS
        val reach = radius + GoalNetConfig.CONTACT_MARGIN + GoalNetConfig.BALL_PUSH_RADIUS
        val searchBox = AABB(
            minOf(prevCenter.x, currCenter.x) - reach,
            minOf(prevCenter.y, currCenter.y) - reach,
            minOf(prevCenter.z, currCenter.z) - reach,
            maxOf(prevCenter.x, currCenter.x) + reach,
            maxOf(prevCenter.y, currCenter.y) + reach,
            maxOf(prevCenter.z, currCenter.z) + reach,
        )
        val nets = level.getEntitiesOfClass(GoalNetEntity::class.java, searchBox)
        if (nets.isEmpty()) return null

        for (net in nets) {
            val rect = net.getRectangle() ?: continue
            val mesh = net.getMesh() ?: continue
            val contact = resolve(level, net, rect, mesh, state, radius, prevCenter, currCenter)
            if (contact != null) return contact
        }
        return null
    }

    private fun resolve(
        level: Level,
        net: GoalNetEntity,
        rect: net.astrorbits.football.util.GoalNetGeometry.NetRectangle,
        mesh: GoalNetMesh,
        state: net.astrorbits.football.physics.FootballPhysicsState,
        radius: Double,
        prevCenter: Vec3,
        currCenter: Vec3,
    ): NetContact? {
        val local = findBestContact(rect, mesh, radius, prevCenter, currCenter) ?: return null

        val velocity = state.linearVelocity
        val n = local.normal
        val vn = velocity.dot(n)

        // 入射侧：穿面时优先用上一帧侧别；仅接触时用当前侧别。
        val side = when {
            kotlin.math.abs(local.signedPrev) > EPS -> Math.signum(local.signedPrev)
            kotlin.math.abs(local.signedNow) > EPS -> Math.signum(local.signedNow)
            else -> {
                val moving = velocity.dot(n)
                if (moving > 0.0) -1.0 else 1.0
            }
        }

        // 仅在朝网运动或已穿透时作用，避免静止/远离时抖动。
        val approaching = vn * side < 0.0
        if (!local.crossed && !approaching) return null

        // 速度分解：切向减速 + 法向吸收 + 条件回弹。
        val vNormal = n.scale(vn)
        val vTangent = velocity.subtract(vNormal)
        val speedIntoNet = Math.abs(vn)
        val penetration = (radius + GoalNetConfig.CONTACT_MARGIN - local.distanceNow).coerceAtLeast(0.0)
        val contactRadius = radius + GoalNetConfig.CONTACT_MARGIN
        val penetrationRatio = (penetration / contactRadius).coerceIn(0.0, 1.0)
        val impactRatio = (speedIntoNet / GoalNetConfig.HARD_CONTACT_SPEED).coerceIn(0.0, 1.0)
        var stretchRatio = (penetrationRatio * 0.7 + impactRatio * 0.3).coerceIn(0.0, 1.0)
        if (local.crossed) {
            stretchRatio = maxOf(stretchRatio, STRETCH_CROSSED_BONUS)
        }

        val tangentRetention = lerp(
            GoalNetConfig.BALL_TANGENT_RETENTION,
            GoalNetConfig.BALL_TANGENT_RETENTION_HARD,
            stretchRatio
        )
        val retainedNormal = vNormal.scale(1.0 - GoalNetConfig.BALL_NORMAL_ABSORPTION)
        // 回弹门控：仅在接近极限拉伸后逐步启用，常态触网只产生阻力。
        val restitutionExtremeRatio = normalizedAbove(
            stretchRatio,
            GoalNetConfig.BALL_RESTITUTION_EXTREME_START
        )
        val restitutionWeight = Math.pow(
            restitutionExtremeRatio,
            GoalNetConfig.BALL_RESTITUTION_EXTREME_EXPONENT
        )
        val restitution = (GoalNetConfig.BALL_RESTITUTION_BASE +
            restitutionWeight * GoalNetConfig.BALL_RESTITUTION_STRETCH_GAIN)
            .coerceAtMost(GoalNetConfig.BALL_RESTITUTION_MAX)
        val reboundNormal = n.scale(side * speedIntoNet * restitution)
        var newVelocity = vTangent.scale(tangentRetention)
            .add(retainedNormal)
            .add(reboundNormal)

        // 去除残留的“朝网内”速度分量，避免贴网旋转与粘附。
        val inwardSpeed = -(newVelocity.dot(n) * side)
        if (inwardSpeed > 0.0) {
            newVelocity = newVelocity.add(n.scale(side * inwardSpeed))
        }

        // 网被顶出的方向 = 球运动穿入方向（-side*n）。
        val pushDir = n.scale(-side)
        val pushAmount = (penetration + speedIntoNet * 0.5) * GoalNetConfig.BALL_PUSH_STRENGTH
        if (pushAmount > 1.0e-4) {
            mesh.applyDisplacement(local.point, pushDir.scale(pushAmount), GoalNetConfig.BALL_PUSH_RADIUS)
            net.markDisturbed()
        }

        // 触网时加强自旋衰减，压得越深（或越接近穿面）衰减越强。
        val spinRetention = lerp(
            GoalNetConfig.BALL_SPIN_RETENTION,
            GoalNetConfig.BALL_SPIN_RETENTION_HARD,
            stretchRatio
        )
        state.angularVelocity = state.angularVelocity.scale(spinRetention)

        // 接近极限拉伸时给额外反推，优先避免持续嵌入/穿透。
        val pushoutRatio = normalizedAbove(stretchRatio, GoalNetConfig.STRETCH_PUSHOUT_START)
        val pushoutSpeed = pushoutRatio * pushoutRatio * GoalNetConfig.STRETCH_PUSHOUT_VELOCITY_GAIN
        if (pushoutSpeed > 1.0e-4) {
            newVelocity = newVelocity.add(n.scale(side * pushoutSpeed))
        }

        // 把球留在入射侧，并与网面保留少量分离距离，减少“黏网”观感。
        // 仅沿法线做穿透修正，避免“吸附到三角形最近点”造成边角突跳。
        val separation = GoalNetConfig.CONTACT_SEPARATION +
            penetration * GoalNetConfig.CONTACT_SEPARATION_FROM_PENETRATION
        val targetSigned = side * (radius + separation)
        val correctionAlongNormal = targetSigned - local.signedNow
        val restCenter = currCenter.add(n.scale(correctionAlongNormal))
        val depResult = FootballBlockDepenetration.depenetrateSphere(level, restCenter, radius)
        val depenetrated = depResult.center
        val blockCorrection = depResult.correction
        if (blockCorrection.lengthSqr() > EPS) {
            val correctionLength = sqrt(blockCorrection.lengthSqr())
            if (correctionLength > EPS) {
                val outward = blockCorrection.scale(1.0 / correctionLength)
                val inward = newVelocity.dot(outward)
                if (inward < 0.0) {
                    // 清除“朝方块内部”速度分量，防止下一帧再次被挤入。
                    newVelocity = newVelocity.subtract(outward.scale(inward))
                }
            }
        }
        state.linearVelocity = newVelocity
        return NetContact(depenetrated)
    }

    private data class LocalContact(
        val point: Vec3,
        val normal: Vec3,
        val distanceNow: Double,
        val signedNow: Double,
        val signedPrev: Double,
        val crossed: Boolean,
        val score: Double,
    )

    /**
     * 基于“实时变形网格三角形”寻找最优接触点：
     * - touching：球心到三角形最近点距离 <= 球半径+余量
     * - crossed：球心轨迹线段穿过三角形平面并落在三角形内
     */
    private fun findBestContact(
        rect: net.astrorbits.football.util.GoalNetGeometry.NetRectangle,
        mesh: GoalNetMesh,
        radius: Double,
        prevCenter: Vec3,
        currCenter: Vec3,
    ): LocalContact? {
        val contactRadius = radius + GoalNetConfig.CONTACT_MARGIN
        val sweptMinX = minOf(prevCenter.x, currCenter.x) - contactRadius
        val sweptMinY = minOf(prevCenter.y, currCenter.y) - contactRadius
        val sweptMinZ = minOf(prevCenter.z, currCenter.z) - contactRadius
        val sweptMaxX = maxOf(prevCenter.x, currCenter.x) + contactRadius
        val sweptMaxY = maxOf(prevCenter.y, currCenter.y) + contactRadius
        val sweptMaxZ = maxOf(prevCenter.z, currCenter.z) + contactRadius
        var best: LocalContact? = null
        val cols = mesh.cols
        val rows = mesh.rows

        for (j in 0 until rows - 1) {
            for (i in 0 until cols - 1) {
                val k00 = mesh.index(i, j)
                val k10 = mesh.index(i + 1, j)
                val k01 = mesh.index(i, j + 1)
                val k11 = mesh.index(i + 1, j + 1)
                val p00 = mesh.nodeWorld(k00)
                val p10 = mesh.nodeWorld(k10)
                val p01 = mesh.nodeWorld(k01)
                val p11 = mesh.nodeWorld(k11)

                // 粗筛：球的 swept AABB 与单元（四点）AABB 不相交时跳过两三角精检。
                if (!cellMayContact(
                        p00, p10, p01, p11,
                        sweptMinX, sweptMinY, sweptMinZ,
                        sweptMaxX, sweptMaxY, sweptMaxZ,
                        contactRadius
                    )
                ) {
                    continue
                }

                best = pickBetter(
                    best,
                    evaluateTriangle(rect.normal, p00, p10, p11, contactRadius, prevCenter, currCenter)
                )
                best = pickBetter(
                    best,
                    evaluateTriangle(rect.normal, p00, p11, p01, contactRadius, prevCenter, currCenter)
                )
            }
        }
        return best
    }

    private fun cellMayContact(
        p00: Vec3,
        p10: Vec3,
        p01: Vec3,
        p11: Vec3,
        sweptMinX: Double,
        sweptMinY: Double,
        sweptMinZ: Double,
        sweptMaxX: Double,
        sweptMaxY: Double,
        sweptMaxZ: Double,
        padding: Double,
    ): Boolean {
        val cellMinX = minOf(p00.x, p10.x, p01.x, p11.x) - padding
        val cellMinY = minOf(p00.y, p10.y, p01.y, p11.y) - padding
        val cellMinZ = minOf(p00.z, p10.z, p01.z, p11.z) - padding
        val cellMaxX = maxOf(p00.x, p10.x, p01.x, p11.x) + padding
        val cellMaxY = maxOf(p00.y, p10.y, p01.y, p11.y) + padding
        val cellMaxZ = maxOf(p00.z, p10.z, p01.z, p11.z) + padding
        return aabbIntersects(
            sweptMinX, sweptMinY, sweptMinZ, sweptMaxX, sweptMaxY, sweptMaxZ,
            cellMinX, cellMinY, cellMinZ, cellMaxX, cellMaxY, cellMaxZ
        )
    }

    private fun aabbIntersects(
        aMinX: Double, aMinY: Double, aMinZ: Double,
        aMaxX: Double, aMaxY: Double, aMaxZ: Double,
        bMinX: Double, bMinY: Double, bMinZ: Double,
        bMaxX: Double, bMaxY: Double, bMaxZ: Double,
    ): Boolean {
        return aMinX <= bMaxX && aMaxX >= bMinX &&
            aMinY <= bMaxY && aMaxY >= bMinY &&
            aMinZ <= bMaxZ && aMaxZ >= bMinZ
    }

    private fun pickBetter(current: LocalContact?, next: LocalContact?): LocalContact? {
        if (next == null) return current
        if (current == null) return next
        return if (next.score > current.score) next else current
    }

    private fun evaluateTriangle(
        preferredNormal: Vec3,
        a: Vec3,
        b: Vec3,
        c: Vec3,
        contactRadius: Double,
        prevCenter: Vec3,
        currCenter: Vec3,
    ): LocalContact? {
        val triNormal = orientedNormal(preferredNormal, a, b, c) ?: return null
        val closestNow = closestPointOnTriangle(currCenter, a, b, c)
        val closestPrev = closestPointOnTriangle(prevCenter, a, b, c)
        val nowOffset = currCenter.subtract(closestNow)
        val prevOffset = prevCenter.subtract(closestPrev)
        val distNow = kotlin.math.sqrt(nowOffset.lengthSqr())
        // 用同一平面参考点（a）计算侧别，避免最近点在边/角跳变时符号抖动。
        val signedNow = currCenter.subtract(a).dot(triNormal)
        val signedPrev = prevCenter.subtract(a).dot(triNormal)
        val touching = distNow <= contactRadius
        val crossed = segmentCrossesTrianglePlane(prevCenter, currCenter, a, b, c, triNormal)
        if (!touching && !crossed) return null

        val score = if (crossed) {
            // 穿面优先级更高，避免高速球只被“附近接触”覆盖。
            10_000.0 + (contactRadius - distNow).coerceAtLeast(0.0)
        } else {
            (contactRadius - distNow).coerceAtLeast(0.0)
        }
        return LocalContact(
            point = closestNow,
            normal = triNormal,
            distanceNow = distNow,
            signedNow = signedNow,
            signedPrev = signedPrev,
            crossed = crossed,
            score = score,
        )
    }

    private fun orientedNormal(preferredNormal: Vec3, a: Vec3, b: Vec3, c: Vec3): Vec3? {
        val ab = b.subtract(a)
        val ac = c.subtract(a)
        var n = ab.cross(ac)
        val lenSqr = n.lengthSqr()
        if (lenSqr < EPS) return null
        n = n.scale(1.0 / kotlin.math.sqrt(lenSqr))
        return if (n.dot(preferredNormal) < 0.0) n.scale(-1.0) else n
    }

    /**
     * Christer Ericson《Real-Time Collision Detection》三角形最近点算法。
     */
    private fun closestPointOnTriangle(p: Vec3, a: Vec3, b: Vec3, c: Vec3): Vec3 {
        val ab = b.subtract(a)
        val ac = c.subtract(a)
        val ap = p.subtract(a)
        val d1 = ab.dot(ap)
        val d2 = ac.dot(ap)
        if (d1 <= 0.0 && d2 <= 0.0) return a

        val bp = p.subtract(b)
        val d3 = ab.dot(bp)
        val d4 = ac.dot(bp)
        if (d3 >= 0.0 && d4 <= d3) return b

        val vc = d1 * d4 - d3 * d2
        if (vc <= 0.0 && d1 >= 0.0 && d3 <= 0.0) {
            val v = d1 / (d1 - d3)
            return a.add(ab.scale(v))
        }

        val cp = p.subtract(c)
        val d5 = ab.dot(cp)
        val d6 = ac.dot(cp)
        if (d6 >= 0.0 && d5 <= d6) return c

        val vb = d5 * d2 - d1 * d6
        if (vb <= 0.0 && d2 >= 0.0 && d6 <= 0.0) {
            val w = d2 / (d2 - d6)
            return a.add(ac.scale(w))
        }

        val va = d3 * d6 - d5 * d4
        if (va <= 0.0 && (d4 - d3) >= 0.0 && (d5 - d6) >= 0.0) {
            val bc = c.subtract(b)
            val w = (d4 - d3) / ((d4 - d3) + (d5 - d6))
            return b.add(bc.scale(w))
        }

        val denom = 1.0 / (va + vb + vc)
        val v = vb * denom
        val w = vc * denom
        return a.add(ab.scale(v)).add(ac.scale(w))
    }

    private fun segmentCrossesTrianglePlane(
        p0: Vec3,
        p1: Vec3,
        a: Vec3,
        b: Vec3,
        c: Vec3,
        n: Vec3,
    ): Boolean {
        val dir = p1.subtract(p0)
        val den = dir.dot(n)
        if (kotlin.math.abs(den) < EPS) return false
        val t = a.subtract(p0).dot(n) / den
        if (t < 0.0 || t > 1.0) return false
        val hit = p0.add(dir.scale(t))
        return pointInTriangle(hit, a, b, c, n)
    }

    private fun pointInTriangle(p: Vec3, a: Vec3, b: Vec3, c: Vec3, n: Vec3): Boolean {
        val c0 = b.subtract(a).cross(p.subtract(a)).dot(n)
        val c1 = c.subtract(b).cross(p.subtract(b)).dot(n)
        val c2 = a.subtract(c).cross(p.subtract(c)).dot(n)
        return c0 >= -EPS && c1 >= -EPS && c2 >= -EPS
    }

    /** 接触结果：球应被放置到的球心位置。 */
    data class NetContact(val restCenter: Vec3)

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t.coerceIn(0.0, 1.0)

    /**
     * 将 [value] 在 [start] 以上区间归一化到 [0, 1]：
     * - value <= start -> 0
     * - value >= 1.0 -> 1
     */
    private fun normalizedAbove(value: Double, start: Double): Double {
        if (start >= 1.0 - EPS) return if (value >= 1.0) 1.0 else 0.0
        return ((value - start) / (1.0 - start)).coerceIn(0.0, 1.0)
    }
}
