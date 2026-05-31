package net.astrorbits.football.physics

import net.astrorbits.football.entity.GoalNetEntity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * 足球与实体化球网的动量交换：
 * - 球撞网时法向动量被大幅吸收（不反弹），切向保留以“贴网下滑”。
 * - 被吸收的动量转化为对附近网格节点的位移，形成网面被顶出的凹陷。
 */
object FootballNetInteraction {

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
            val contact = resolve(net, rect, mesh, state, radius, prevCenter, currCenter)
            if (contact != null) return contact
        }
        return null
    }

    private fun resolve(
        net: GoalNetEntity,
        rect: net.astrorbits.football.util.GoalNetGeometry.NetRectangle,
        mesh: GoalNetMesh,
        state: net.astrorbits.football.physics.FootballPhysicsState,
        radius: Double,
        prevCenter: Vec3,
        currCenter: Vec3,
    ): NetContact? {
        val n = rect.normal
        val o = rect.origin
        val distNow = currCenter.subtract(o).dot(n)
        val distPrev = prevCenter.subtract(o).dot(n)

        // 投影到网面，确认是否在矩形范围内。
        val proj = currCenter.subtract(n.scale(distNow))
        val rel = proj.subtract(o)
        val u = rel.dot(rect.uAxis)
        val v = rel.dot(rect.vAxis)
        if (u < -radius || u > rect.uLength + radius) return null
        if (v < -radius || v > rect.vLength + radius) return null

        val crossed = distPrev * distNow < 0.0
        val touching = Math.abs(distNow) < radius + GoalNetConfig.CONTACT_MARGIN
        if (!crossed && !touching) return null

        val velocity = state.linearVelocity
        val vn = velocity.dot(n)

        // 入射侧：以上一帧所在侧为准（穿透时用 distPrev，否则用 distNow）。
        val side = if (distPrev != 0.0) Math.signum(distPrev) else Math.signum(distNow).let { if (it == 0.0) 1.0 else it }

        // 仅在朝网运动或已穿透时作用，避免静止/远离时抖动。
        val approaching = vn * side < 0.0
        if (!crossed && !approaching) return null

        // 速度分解：法向吸收，切向保留。
        val vNormal = n.scale(vn)
        val vTangent = velocity.subtract(vNormal)
        val newVelocity = vTangent.scale(GoalNetConfig.BALL_TANGENT_RETENTION)
            .add(vNormal.scale(1.0 - GoalNetConfig.BALL_NORMAL_ABSORPTION))

        // 网被顶出的方向 = 球运动穿入方向（-side*n）。
        val pushDir = n.scale(-side)
        val speedIntoNet = Math.abs(vn)
        val penetration = (radius + GoalNetConfig.CONTACT_MARGIN - Math.abs(distNow)).coerceAtLeast(0.0)
        val pushAmount = (penetration + speedIntoNet * 0.5) * GoalNetConfig.BALL_PUSH_STRENGTH
        if (pushAmount > 1.0e-4) {
            mesh.applyDisplacement(proj, pushDir.scale(pushAmount), GoalNetConfig.BALL_PUSH_RADIUS)
            net.markDisturbed()
        }

        // 把球留在入射侧、贴着网面（防止穿过）。
        val restCenter = proj.add(n.scale(side * radius))
        state.linearVelocity = newVelocity
        return NetContact(restCenter)
    }

    /** 接触结果：球应被放置到的球心位置。 */
    data class NetContact(val restCenter: Vec3)
}
