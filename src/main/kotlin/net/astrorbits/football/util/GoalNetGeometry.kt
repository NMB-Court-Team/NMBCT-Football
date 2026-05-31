package net.astrorbits.football.util

import net.astrorbits.football.block.GoalNetAnchorBlock
import net.astrorbits.football.physics.GoalNetConfig
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * 由四个锚点世界坐标构造球网矩形的几何工具。
 *
 * 合法球网要求：四个锚点能构成一个轴对齐的、水平或竖直的非退化长方形。
 * 锚点坐标由 [GoalNetAnchorBlock.getAnchorPos] 决定，不再假定位于方块中心。
 */
object GoalNetGeometry {

    private const val EPS = 1.0e-4

    /** 校验失败原因，便于在 actionbar 给出明确提示。 */
    enum class Failure(val translationKey: String) {
        NOT_ENOUGH_POINTS("message.nmbct-football.goal_net.fail.not_enough"),
        DUPLICATE_POINTS("message.nmbct-football.goal_net.fail.duplicate"),
        NOT_COPLANAR("message.nmbct-football.goal_net.fail.not_coplanar"),
        NOT_RECTANGLE("message.nmbct-football.goal_net.fail.not_rectangle"),
        DEGENERATE("message.nmbct-football.goal_net.fail.degenerate"),
        INVALID_ANCHOR("message.nmbct-football.goal_net.fail.invalid_anchor"),
    }

    /**
     * 球网矩形：以 [origin] 为一角，沿单位向量 [uAxis]、[vAxis] 张成，
     * 边长分别为 [uLength]、[vLength]，平面法向为 [normal]。
     */
    data class NetRectangle(
        val origin: Vec3,
        val uAxis: Vec3,
        val vAxis: Vec3,
        val uLength: Double,
        val vLength: Double,
        val normal: Vec3,
    )

    sealed interface Result {
        data class Success(val rectangle: NetRectangle) : Result
        data class Failed(val failure: Failure) : Result
    }

    /** 从世界中读取锚点方块的真实锚点坐标；若非锚点方块则返回 null。 */
    fun resolveAnchorPositions(level: Level, blocks: List<BlockPos>): List<Vec3>? {
        val positions = ArrayList<Vec3>(blocks.size)
        for (pos in blocks) {
            val state = level.getBlockState(pos)
            val block = state.block as? GoalNetAnchorBlock ?: return null
            positions.add(block.getAnchorPos(pos, state))
        }
        return positions
    }

    fun validate(anchorPositions: List<Vec3>): Result {
        if (anchorPositions.size < 4) return Result.Failed(Failure.NOT_ENOUGH_POINTS)
        if (distinctPositions(anchorPositions).size != 4) {
            return Result.Failed(Failure.DUPLICATE_POINTS)
        }

        val xs = distinctAxis(anchorPositions.map { it.x })
        val ys = distinctAxis(anchorPositions.map { it.y })
        val zs = distinctAxis(anchorPositions.map { it.z })

        return when {
            xs.size == 1 -> buildRectangle(anchorPositions, Plane.X, xs[0], ys, zs)
            ys.size == 1 -> buildRectangle(anchorPositions, Plane.Y, ys[0], xs, zs)
            zs.size == 1 -> buildRectangle(anchorPositions, Plane.Z, zs[0], xs, ys)
            else -> Result.Failed(Failure.NOT_COPLANAR)
        }
    }

    private enum class Plane { X, Y, Z }

    private fun buildRectangle(
        points: List<Vec3>,
        plane: Plane,
        fixed: Double,
        axisA: List<Double>,
        axisB: List<Double>,
    ): Result {
        if (axisA.size != 2 || axisB.size != 2) {
            return if (axisA.size < 2 || axisB.size < 2) {
                Result.Failed(Failure.DEGENERATE)
            } else {
                Result.Failed(Failure.NOT_RECTANGLE)
            }
        }

        val a0 = axisA[0]; val a1 = axisA[1]
        val b0 = axisB[0]; val b1 = axisB[1]

        val expectedCorners = when (plane) {
            Plane.X -> listOf(
                Vec3(fixed, a0, b0), Vec3(fixed, a0, b1),
                Vec3(fixed, a1, b0), Vec3(fixed, a1, b1),
            )
            Plane.Y -> listOf(
                Vec3(a0, fixed, b0), Vec3(a0, fixed, b1),
                Vec3(a1, fixed, b0), Vec3(a1, fixed, b1),
            )
            Plane.Z -> listOf(
                Vec3(a0, b0, fixed), Vec3(a0, b1, fixed),
                Vec3(a1, b0, fixed), Vec3(a1, b1, fixed),
            )
        }
        if (!matchesCorners(points, expectedCorners)) {
            return Result.Failed(Failure.NOT_RECTANGLE)
        }

        val rect = when (plane) {
            Plane.X -> NetRectangle(
                origin = Vec3(fixed, a0, b0),
                uAxis = Vec3(0.0, 0.0, 1.0),
                vAxis = Vec3(0.0, 1.0, 0.0),
                uLength = b1 - b0,
                vLength = a1 - a0,
                normal = Vec3(1.0, 0.0, 0.0),
            )
            Plane.Y -> NetRectangle(
                origin = Vec3(a0, fixed, b0),
                uAxis = Vec3(1.0, 0.0, 0.0),
                vAxis = Vec3(0.0, 0.0, 1.0),
                uLength = a1 - a0,
                vLength = b1 - b0,
                normal = Vec3(0.0, 1.0, 0.0),
            )
            Plane.Z -> NetRectangle(
                origin = Vec3(a0, b0, fixed),
                uAxis = Vec3(1.0, 0.0, 0.0),
                vAxis = Vec3(0.0, 1.0, 0.0),
                uLength = a1 - a0,
                vLength = b1 - b0,
                normal = Vec3(0.0, 0.0, 1.0),
            )
        }

        if (rect.uLength < 1.0e-3 || rect.vLength < 1.0e-3) {
            return Result.Failed(Failure.DEGENERATE)
        }
        return Result.Success(rect)
    }

    private fun distinctPositions(points: List<Vec3>): List<Vec3> {
        val result = ArrayList<Vec3>(points.size)
        for (p in points) {
            if (result.none { near(it, p) }) {
                result.add(p)
            }
        }
        return result
    }

    private fun distinctAxis(values: List<Double>): List<Double> {
        val sorted = values.sorted()
        val result = ArrayList<Double>(sorted.size)
        for (v in sorted) {
            if (result.isEmpty() || kotlin.math.abs(v - result.last()) > EPS) {
                result.add(v)
            }
        }
        return result
    }

    private fun matchesCorners(points: List<Vec3>, expected: List<Vec3>): Boolean {
        if (points.size != expected.size) return false
        val remaining = expected.toMutableList()
        for (p in points) {
            val idx = remaining.indexOfFirst { near(it, p) }
            if (idx < 0) return false
            remaining.removeAt(idx)
        }
        return remaining.isEmpty()
    }

    private fun near(a: Vec3, b: Vec3): Boolean =
        kotlin.math.abs(a.x - b.x) <= EPS &&
            kotlin.math.abs(a.y - b.y) <= EPS &&
            kotlin.math.abs(a.z - b.z) <= EPS

    /** 依据目标间距与上下限推导某一轴向的节点数。 */
    fun resolveNodeCount(length: Double): Int {
        val raw = Math.round(length / GoalNetConfig.TARGET_NODE_SPACING).toInt() + 1
        return raw.coerceIn(GoalNetConfig.MIN_NODES_PER_AXIS, GoalNetConfig.MAX_NODES_PER_AXIS)
    }
}
