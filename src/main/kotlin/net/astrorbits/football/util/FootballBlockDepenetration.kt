package net.astrorbits.football.util

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.floor

/**
 * 足球（球心+半径）与世界方块碰撞体的防穿透修正。
 */
object FootballBlockDepenetration {
    private const val DEFAULT_STEPS = 6
    private const val DEFAULT_EPSILON = 0.002

    data class Result(
        val center: Vec3,
        val correction: Vec3,
    )

    fun depenetrateSphere(
        level: Level,
        center: Vec3,
        radius: Double,
        maxSteps: Int = DEFAULT_STEPS,
        epsilon: Double = DEFAULT_EPSILON,
    ): Result {
        var corrected = center
        var totalCorrection = Vec3.ZERO
        repeat(maxSteps.coerceAtLeast(1)) {
            val push = computeBlockPush(level, corrected, radius, epsilon) ?: return Result(corrected, totalCorrection)
            corrected = corrected.add(push)
            totalCorrection = totalCorrection.add(push)
        }
        return Result(corrected, totalCorrection)
    }

    private fun computeBlockPush(level: Level, center: Vec3, radius: Double, epsilon: Double): Vec3? {
        val minX = floor(center.x - radius).toInt()
        val maxX = floor(center.x + radius).toInt()
        val minY = floor(center.y - radius).toInt()
        val maxY = floor(center.y + radius).toInt()
        val minZ = floor(center.z - radius).toInt()
        val maxZ = floor(center.z + radius).toInt()

        var bestPush: Vec3? = null
        var bestPushLen = Double.MAX_VALUE
        for (bx in minX..maxX) {
            for (by in minY..maxY) {
                for (bz in minZ..maxZ) {
                    val pos = BlockPos(bx, by, bz)
                    val state = level.getBlockState(pos)
                    if (state.isAir) continue
                    val shape = state.getCollisionShape(level, pos)
                    if (shape.isEmpty) continue
                    for (box in shape.toAabbs()) {
                        val worldMinX = box.minX + bx
                        val worldMinY = box.minY + by
                        val worldMinZ = box.minZ + bz
                        val worldMaxX = box.maxX + bx
                        val worldMaxY = box.maxY + by
                        val worldMaxZ = box.maxZ + bz
                        if (center.x < worldMinX - radius || center.x > worldMaxX + radius ||
                            center.y < worldMinY - radius || center.y > worldMaxY + radius ||
                            center.z < worldMinZ - radius || center.z > worldMaxZ + radius
                        ) {
                            continue
                        }

                        val pushLeft = (worldMinX - radius) - center.x - epsilon
                        val pushRight = (worldMaxX + radius) - center.x + epsilon
                        val pushDown = (worldMinY - radius) - center.y - epsilon
                        val pushUp = (worldMaxY + radius) - center.y + epsilon
                        val pushBack = (worldMinZ - radius) - center.z - epsilon
                        val pushFront = (worldMaxZ + radius) - center.z + epsilon

                        val candidates = arrayOf(
                            Vec3(pushLeft, 0.0, 0.0),
                            Vec3(pushRight, 0.0, 0.0),
                            Vec3(0.0, pushDown, 0.0),
                            Vec3(0.0, pushUp, 0.0),
                            Vec3(0.0, 0.0, pushBack),
                            Vec3(0.0, 0.0, pushFront),
                        )
                        for (candidate in candidates) {
                            val len = candidate.lengthSqr()
                            if (len < bestPushLen) {
                                bestPushLen = len
                                bestPush = candidate
                            }
                        }
                    }
                }
            }
        }
        return if (bestPushLen < Double.MAX_VALUE) bestPush else null
    }
}
