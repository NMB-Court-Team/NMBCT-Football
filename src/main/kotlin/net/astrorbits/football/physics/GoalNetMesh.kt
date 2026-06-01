package net.astrorbits.football.physics

import net.astrorbits.football.util.GoalNetGeometry.NetRectangle
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.floor

/**
 * 球网的质点-弹簧网格（服务端权威模拟）。
 *
 * - 节点排布在矩形 [rect] 张成的平面网格上，边界节点被钉死在锚框上。
 * - 内部节点用 Verlet 积分 + 基于位置的距离约束（PBD）求解。
 * - [slack] 不再直接放大弹簧静长，而是通过“重力增益 + 约束柔化”表达松垮程度，
 *   以避免大平面闭合边界下的起皱/山脊模态。
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
    private val corrX = DoubleArray(nodeCount)
    private val corrY = DoubleArray(nodeCount)
    private val corrZ = DoubleArray(nodeCount)
    private val corrW = DoubleArray(nodeCount)

    private data class Spring(
        val a: Int,
        val b: Int,
        val baseLength: Double,
        val stiffness: Double,
    )
    private val springs = ArrayList<Spring>()

    init {
        buildFrame()
        buildSprings()
        resetToFrame()
        applyInitialSag()
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
                // 一阶结构弹簧（主约束）。
                if (i + 1 < cols) springs.add(makeSpring(a, index(i + 1, j), 1.0))
                if (j + 1 < rows) springs.add(makeSpring(a, index(i, j + 1), 1.0))
                // 二阶结构弹簧（弱约束）：抑制条纹塌陷，但避免高松弛度下形成对称鼓包模态。
                if (i + 2 < cols) springs.add(makeSpring(a, index(i + 2, j), 0.35))
                if (j + 2 < rows) springs.add(makeSpring(a, index(i, j + 2), 0.35))
                // 剪切（对角）弹簧增强稳定性，减少网面抖动。
                if (i + 1 < cols && j + 1 < rows) springs.add(makeSpring(a, index(i + 1, j + 1), 0.65))
                if (i + 1 < cols && j + 1 < rows) springs.add(makeSpring(index(i + 1, j), index(i, j + 1), 0.65))
            }
        }
    }

    private fun makeSpring(a: Int, b: Int, stiffness: Double): Spring {
        val dx = frameX[a] - frameX[b]
        val dy = frameY[a] - frameY[b]
        val dz = frameZ[a] - frameZ[b]
        return Spring(a, b, Math.sqrt(dx * dx + dy * dy + dz * dz), stiffness)
    }

    fun resetToFrame() {
        for (k in 0 until nodeCount) {
            posX[k] = frameX[k]; posY[k] = frameY[k]; posZ[k] = frameZ[k]
            prevX[k] = frameX[k]; prevY[k] = frameY[k]; prevZ[k] = frameZ[k]
        }
    }

    /**
     * 创建时根据 slack 直接给内部节点一个基础下垂，避免“刚创建先完全拉平”。
     * 下垂在网中心最明显，靠边缘逐渐衰减到 0。
     */
    private fun applyInitialSag() {
        if (slack <= 1.0e-6) return
        val baseSag = slack * GoalNetConfig.INITIAL_SAG_PER_SLACK
        if (baseSag <= 1.0e-6) return
        for (j in 0 until rows) {
            val fv = if (rows == 1) 0.0 else j.toDouble() / (rows - 1)
            val edgeV = 1.0 - abs(fv * 2.0 - 1.0)
            for (i in 0 until cols) {
                val k = index(i, j)
                if (pinned[k]) continue
                val fu = if (cols == 1) 0.0 else i.toDouble() / (cols - 1)
                val edgeU = 1.0 - abs(fu * 2.0 - 1.0)
                val centerWeight = (edgeU * edgeV).coerceIn(0.0, 1.0)
                if (centerWeight <= 1.0e-6) continue
                val sag = baseSag * centerWeight
                posY[k] -= sag
                prevY[k] -= sag
            }
        }
    }

    fun setSlack(value: Double) {
        slack = value.coerceIn(GoalNetConfig.MIN_SLACK, GoalNetConfig.MAX_SLACK)
    }

    /** 推进一步模拟。返回本步内节点的总位移平方和（用于静止判定）。 */
    fun step(): Double {
        var motionSqr = 0.0
        val gravity = GoalNetConfig.GRAVITY * (1.0 + slack * GoalNetConfig.SLACK_GRAVITY_GAIN)
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
            posY[k] += vy - gravity
            posZ[k] += vz
            motionSqr += vx * vx + vy * vy + vz * vz
        }

        repeat(GoalNetConfig.CONSTRAINT_ITERATIONS) {
            accumulateSpringCorrections()
            applyAccumulatedCorrections()
        }
        return motionSqr
    }

    /**
     * 批量累计约束修正，避免按固定遍历顺序“就地修正”带来的方向偏置。
     * 这对水平球网的列间均匀下垂尤其关键。
     */
    private fun accumulateSpringCorrections() {
        Arrays.fill(corrX, 0.0)
        Arrays.fill(corrY, 0.0)
        Arrays.fill(corrZ, 0.0)
        Arrays.fill(corrW, 0.0)
        val stiffnessScale =
            (1.0 - slack * GoalNetConfig.SLACK_STIFFNESS_REDUCTION)
                .coerceAtLeast(GoalNetConfig.MIN_STIFFNESS_SCALE_AT_MAX_SLACK)
        for (s in springs) {
            accumulateSpringCorrection(s.a, s.b, s.baseLength, s.stiffness * stiffnessScale)
        }
    }

    private fun accumulateSpringCorrection(a: Int, b: Int, rest: Double, stiffness: Double) {
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
        val s = stiffness.coerceIn(0.0, 1.0)
        if (s <= 1.0e-9) return
        val corrDx = dx * diff * s
        val corrDy = dy * diff * s
        val corrDz = dz * diff * s
        if (wa > 0.0) {
            corrX[a] += corrDx * ka
            corrY[a] += corrDy * ka
            corrZ[a] += corrDz * ka
            corrW[a] += ka * s
        }
        if (wb > 0.0) {
            corrX[b] -= corrDx * kb
            corrY[b] -= corrDy * kb
            corrZ[b] -= corrDz * kb
            corrW[b] += kb * s
        }
    }

    private fun applyAccumulatedCorrections() {
        for (k in 0 until nodeCount) {
            val weight = corrW[k]
            if (weight <= 1.0e-9 || pinned[k]) continue
            posX[k] += corrX[k] / weight
            posY[k] += corrY[k] / weight
            posZ[k] += corrZ[k] / weight
        }
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

    /**
     * 与世界方块碰撞：当节点进入方块碰撞形状时，沿 +Y 方向推出到方块表面上方。
     * 返回是否发生过碰撞修正。
     */
    fun resolveBlockCollisions(level: Level): Boolean {
        val radius = GoalNetConfig.NET_BLOCK_COLLISION_RADIUS
        val epsilon = GoalNetConfig.NET_BLOCK_COLLISION_EPSILON
        var corrected = false
        for (k in 0 until nodeCount) {
            if (pinned[k]) continue
            val x = posX[k]
            val y = posY[k]
            val z = posZ[k]
            val minX = floor(x - radius).toInt()
            val maxX = floor(x + radius).toInt()
            val minY = floor(y - radius).toInt()
            val maxY = floor(y + radius).toInt()
            val minZ = floor(z - radius).toInt()
            val maxZ = floor(z + radius).toInt()

            var pushY = y
            for (bx in minX..maxX) {
                for (by in minY..maxY) {
                    for (bz in minZ..maxZ) {
                        val blockPos = BlockPos(bx, by, bz)
                        val state = level.getBlockState(blockPos)
                        if (state.isAir) continue
                        val shape = state.getCollisionShape(level, blockPos)
                        if (shape.isEmpty) continue
                        for (box in shape.toAabbs()) {
                            val boxMinX = box.minX + bx
                            val boxMinY = box.minY + by
                            val boxMinZ = box.minZ + bz
                            val boxMaxX = box.maxX + bx
                            val boxMaxY = box.maxY + by
                            val boxMaxZ = box.maxZ + bz
                            val intersects =
                                x >= boxMinX - radius && x <= boxMaxX + radius &&
                                    z >= boxMinZ - radius && z <= boxMaxZ + radius &&
                                    y >= boxMinY - radius && y <= boxMaxY + radius
                            if (!intersects) continue
                            pushY = maxOf(pushY, boxMaxY + radius + epsilon)
                        }
                    }
                }
            }

            if (pushY > y + 1.0e-9) {
                posY[k] = pushY
                // 清除法向（竖直）残余速度，避免下一帧再次硬穿入。
                prevY[k] = pushY
                corrected = true
            }
        }
        return corrected
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
