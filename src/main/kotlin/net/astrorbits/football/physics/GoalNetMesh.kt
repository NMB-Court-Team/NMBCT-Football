package net.astrorbits.football.physics

import net.astrorbits.football.util.GoalNetGeometry.NetRectangle
import net.minecraft.world.phys.Vec3

/**
 * 球网的质点-弹簧网格（服务端权威模拟）。
 *
 * - 节点排布在矩形 [rect] 张成的平面网格上，边界节点被钉死在锚框上。
 * - 内部节点用 Verlet 积分 + 基于位置的距离约束（PBD）求解，弹簧静止长度随 [slack] 放大，
 *   因此重力会让网自然下垂。
 * - 坐标全部为世界坐标（[Vec3]），同步时再转为相对实体原点的偏移。
 */
class GoalNetMesh(
    val rect: NetRectangle,
    slack: Double,
) {
    val cols: Int = net.astrorbits.football.util.GoalNetGeometry.resolveNodeCount(rect.uLength)
    val rows: Int = net.astrorbits.football.util.GoalNetGeometry.resolveNodeCount(rect.vLength)
    val nodeCount: Int = cols * rows

    var slack: Double = slack.coerceIn(GoalNetConfig.MIN_SLACK, GoalNetConfig.MAX_SLACK)
        private set

    private val posX = DoubleArray(nodeCount)
    private val posY = DoubleArray(nodeCount)
    private val posZ = DoubleArray(nodeCount)
    private val prevX = DoubleArray(nodeCount)
    private val prevY = DoubleArray(nodeCount)
    private val prevZ = DoubleArray(nodeCount)
    private val frameX = DoubleArray(nodeCount)
    private val frameY = DoubleArray(nodeCount)
    private val frameZ = DoubleArray(nodeCount)
    private val pinned = BooleanArray(nodeCount)

    private data class Spring(val a: Int, val b: Int, val baseLength: Double)
    private val springs = ArrayList<Spring>()

    init {
        buildFrame()
        buildSprings()
        resetToFrame()
    }

    fun index(i: Int, j: Int): Int = j * cols + i

    private fun buildFrame() {
        for (j in 0 until rows) {
            val fv = if (rows == 1) 0.0 else j.toDouble() / (rows - 1)
            for (i in 0 until cols) {
                val fu = if (cols == 1) 0.0 else i.toDouble() / (cols - 1)
                val x = rect.origin.x + rect.uAxis.x * rect.uLength * fu + rect.vAxis.x * rect.vLength * fv
                val y = rect.origin.y + rect.uAxis.y * rect.uLength * fu + rect.vAxis.y * rect.vLength * fv
                val z = rect.origin.z + rect.uAxis.z * rect.uLength * fu + rect.vAxis.z * rect.vLength * fv
                val k = index(i, j)
                frameX[k] = x; frameY[k] = y; frameZ[k] = z
                pinned[k] = i == 0 || i == cols - 1 || j == 0 || j == rows - 1
            }
        }
    }

    private fun buildSprings() {
        springs.clear()
        for (j in 0 until rows) {
            for (i in 0 until cols) {
                val a = index(i, j)
                if (i + 1 < cols) springs.add(makeSpring(a, index(i + 1, j)))
                if (j + 1 < rows) springs.add(makeSpring(a, index(i, j + 1)))
                // 剪切（对角）弹簧增强稳定性，减少网面抖动。
                if (i + 1 < cols && j + 1 < rows) springs.add(makeSpring(a, index(i + 1, j + 1)))
                if (i + 1 < cols && j + 1 < rows) springs.add(makeSpring(index(i + 1, j), index(i, j + 1)))
            }
        }
    }

    private fun makeSpring(a: Int, b: Int): Spring {
        val dx = frameX[a] - frameX[b]
        val dy = frameY[a] - frameY[b]
        val dz = frameZ[a] - frameZ[b]
        return Spring(a, b, Math.sqrt(dx * dx + dy * dy + dz * dz))
    }

    fun resetToFrame() {
        for (k in 0 until nodeCount) {
            posX[k] = frameX[k]; posY[k] = frameY[k]; posZ[k] = frameZ[k]
            prevX[k] = frameX[k]; prevY[k] = frameY[k]; prevZ[k] = frameZ[k]
        }
    }

    fun setSlack(value: Double) {
        slack = value.coerceIn(GoalNetConfig.MIN_SLACK, GoalNetConfig.MAX_SLACK)
    }

    /** 推进一步模拟。返回本步内节点的总位移平方和（用于静止判定）。 */
    fun step(): Double {
        var motionSqr = 0.0
        for (k in 0 until nodeCount) {
            if (pinned[k]) {
                posX[k] = frameX[k]; posY[k] = frameY[k]; posZ[k] = frameZ[k]
                prevX[k] = frameX[k]; prevY[k] = frameY[k]; prevZ[k] = frameZ[k]
                continue
            }
            val vx = (posX[k] - prevX[k]) * GoalNetConfig.DAMPING
            val vy = (posY[k] - prevY[k]) * GoalNetConfig.DAMPING
            val vz = (posZ[k] - prevZ[k]) * GoalNetConfig.DAMPING
            prevX[k] = posX[k]; prevY[k] = posY[k]; prevZ[k] = posZ[k]
            posX[k] += vx
            posY[k] += vy - GoalNetConfig.GRAVITY
            posZ[k] += vz
            motionSqr += vx * vx + vy * vy + vz * vz
        }

        val restScale = 1.0 + slack
        repeat(GoalNetConfig.CONSTRAINT_ITERATIONS) {
            for (s in springs) satisfy(s.a, s.b, s.baseLength * restScale)
        }
        return motionSqr
    }

    private fun satisfy(a: Int, b: Int, rest: Double) {
        val dx = posX[b] - posX[a]
        val dy = posY[b] - posY[a]
        val dz = posZ[b] - posZ[a]
        val d = Math.sqrt(dx * dx + dy * dy + dz * dz)
        if (d < 1.0e-6) return
        val wa = if (pinned[a]) 0.0 else 1.0
        val wb = if (pinned[b]) 0.0 else 1.0
        val sum = wa + wb
        if (sum == 0.0) return
        val diff = (d - rest) / d
        val ka = wa / sum
        val kb = wb / sum
        posX[a] += dx * diff * ka; posY[a] += dy * diff * ka; posZ[a] += dz * diff * ka
        posX[b] -= dx * diff * kb; posY[b] -= dy * diff * kb; posZ[b] -= dz * diff * kb
    }

    /** 在 [point] 周围 [radius] 范围内对内部节点施加位移（Verlet 自动转换为速度）。 */
    fun applyDisplacement(point: Vec3, displacement: Vec3, radius: Double) {
        val r2 = radius * radius
        for (k in 0 until nodeCount) {
            if (pinned[k]) continue
            val dx = posX[k] - point.x
            val dy = posY[k] - point.y
            val dz = posZ[k] - point.z
            val distSqr = dx * dx + dy * dy + dz * dz
            if (distSqr > r2) continue
            val w = 1.0 - Math.sqrt(distSqr) / radius
            posX[k] += displacement.x * w
            posY[k] += displacement.y * w
            posZ[k] += displacement.z * w
        }
    }

    fun nodeWorld(k: Int): Vec3 = Vec3(posX[k], posY[k], posZ[k])

    fun isPinned(k: Int): Boolean = pinned[k]

    /** 将节点位置写入相对 [origin] 的 float 数组（长度 nodeCount*3），用于网络同步。 */
    fun writeRelative(origin: Vec3, out: FloatArray) {
        for (k in 0 until nodeCount) {
            out[k * 3] = (posX[k] - origin.x).toFloat()
            out[k * 3 + 1] = (posY[k] - origin.y).toFloat()
            out[k * 3 + 2] = (posZ[k] - origin.z).toFloat()
        }
    }
}
