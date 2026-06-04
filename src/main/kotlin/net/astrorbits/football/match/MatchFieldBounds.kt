package net.astrorbits.football.match

import kotlin.math.abs

/**
 * 由球门线与边线围成的球场水平矩形；用于比赛辅助功能（如全场足球位置指示）的范围判定。
 */
object MatchFieldBounds {
    const val INDICATOR_EXPANSION = 64.0

    data class HorizontalRect(
        val minX: Double,
        val maxX: Double,
        val minZ: Double,
        val maxZ: Double,
    ) {
        fun expanded(margin: Double) = HorizontalRect(
            minX - margin,
            maxX + margin,
            minZ - margin,
            maxZ + margin,
        )

        fun containsHorizontal(x: Double, z: Double): Boolean =
            x in minX..maxX && z in minZ..maxZ
    }

    /** 球场边线围成的水平矩形；配置无效时返回 null。 */
    fun pitchRect(config: MatchConfig): HorizontalRect? {
        val sidelineAxis = config.sidelineA.axis.lowercase()
        if (sidelineAxis != config.sidelineB.axis.lowercase()) {
            return null
        }
        val sidelineBounds = sidelineBounds(config.sidelineA, config.sidelineB) ?: return null
        val goalAxis = goalLineAxis(config.goalA, config.goalB) ?: return null

        return when {
            sidelineAxis == "x" && goalAxis == "z" -> HorizontalRect(
                minX = goalBounds(config.goalA, config.goalB, "x").first,
                maxX = goalBounds(config.goalA, config.goalB, "x").second,
                minZ = sidelineBounds.first,
                maxZ = sidelineBounds.second,
            )
            sidelineAxis == "z" && goalAxis == "x" -> HorizontalRect(
                minX = sidelineBounds.first,
                maxX = sidelineBounds.second,
                minZ = goalBounds(config.goalA, config.goalB, "z").first,
                maxZ = goalBounds(config.goalA, config.goalB, "z").second,
            )
            else -> null
        }
    }

    /** 球场矩形向外扩展 [INDICATOR_EXPANSION] 格；Y 方向不受限。 */
    fun indicatorRect(config: MatchConfig): HorizontalRect? =
        pitchRect(config)?.expanded(INDICATOR_EXPANSION)

    private fun sidelineBounds(a: SidelineConfig, b: SidelineConfig): Pair<Double, Double>? {
        if (a.axis.lowercase() != b.axis.lowercase()) {
            return null
        }
        var min = Double.NEGATIVE_INFINITY
        var max = Double.POSITIVE_INFINITY
        for (s in listOf(a, b)) {
            when {
                s.positiveInside -> min = maxOf(min, s.coord)
                else -> max = minOf(max, s.coord)
            }
        }
        if (!min.isFinite() || !max.isFinite() || min >= max) {
            return null
        }
        return min to max
    }

    private fun goalBounds(goalA: GoalConfig, goalB: GoalConfig, axis: String): Pair<Double, Double> {
        val values = when (axis) {
            "x" -> listOf(goalA.x1, goalA.x2, goalB.x1, goalB.x2)
            "z" -> listOf(goalA.z1, goalA.z2, goalB.z1, goalB.z2)
            else -> emptyList()
        }
        return values.min() to values.max()
    }

    private fun goalLineAxis(goalA: GoalConfig, goalB: GoalConfig, epsilon: Double = 1e-3): String? {
        val constantZ = abs(goalA.z1 - goalA.z2) < epsilon && abs(goalB.z1 - goalB.z2) < epsilon
        val constantX = abs(goalA.x1 - goalA.x2) < epsilon && abs(goalB.x1 - goalB.x2) < epsilon
        return when {
            constantZ -> "z"
            constantX -> "x"
            else -> null
        }
    }
}
