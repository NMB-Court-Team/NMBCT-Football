package net.astrorbits.football.match

import kotlin.math.abs
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

/**
 * 基于 [MatchConfig] 的球场区域判定（仅使用水平坐标 x、z，忽略 y）。
 *
 * 「某一边」对应 [TeamSide]：`A` 为 [MatchConfig.goalA] 一侧，`B` 为 [MatchConfig.goalB] 一侧。
 */
object MatchFieldAreaUtil {

    private enum class LongAxis {
        X,
        Z,
    }

    /** 指定边对应的球门配置。 */
    fun goalForSide(config: MatchConfig, side: TeamSide): GoalConfig = when (side) {
        TeamSide.A -> config.goalA
        TeamSide.B -> config.goalB
    }

    /** 玩家是否处于 [side] 对应球队的半场（与中圈同轴、过开球点的分界线划分；分界线上的点两边均视为在内）。 */
    fun isPlayerInHalf(
        player: Player,
        side: TeamSide,
        config: MatchConfig = MatchConfigHolder.current,
    ): Boolean = isInHalf(config, side, player.x, player.z)

    /** 玩家是否处于 [side] 一侧的大禁区（罚球区）内。 */
    fun isPlayerInPenaltyArea(
        player: Player,
        side: TeamSide,
        config: MatchConfig = MatchConfigHolder.current,
    ): Boolean = isInPenaltyArea(config, side, player.x, player.z)

    /** 玩家是否处于 [side] 一侧的小禁区（球门区）内。 */
    fun isPlayerInGoalArea(
        player: Player,
        side: TeamSide,
        config: MatchConfig = MatchConfigHolder.current,
    ): Boolean = isInGoalArea(config, side, player.x, player.z)

    /**
     * 玩家是否处于 [side] 一侧的罚球弧内：以点球点为圆心、[HalfAreaConfig.penaltyArcRadius] 为半径的圆内，
     * 且不在该侧大禁区内。
     */
    fun isPlayerInPenaltyArc(
        player: Player,
        side: TeamSide,
        config: MatchConfig = MatchConfigHolder.current,
    ): Boolean = isInPenaltyArc(config, side, player.x, player.z)

    /** 玩家是否处于中圆内（圆心为 [MatchConfig.kickOff]，半径为 [MatchConfig.centerCircleRadius]）。 */
    fun isPlayerInCenterCircle(
        player: Player,
        config: MatchConfig = MatchConfigHolder.current,
    ): Boolean = isInCenterCircle(config, player.x, player.z)

    fun isInHalf(config: MatchConfig, side: TeamSide, x: Double, z: Double): Boolean {
        val orientation = pitchOrientation(config) ?: return false
        val goalCoord = longCoord(goalForSide(config, side).goalCenter(), orientation)
        val pointCoord = longCoord(x, z, orientation)
        return (pointCoord - orientation.midfieldCoord) * (goalCoord - orientation.midfieldCoord) >= 0.0
    }

    fun isInPenaltyArea(config: MatchConfig, side: TeamSide, x: Double, z: Double): Boolean {
        val halfArea = goalForSide(config, side).halfArea
        return horizontalRect(halfArea.penaltyAreaCorner1, halfArea.penaltyAreaCorner2)
            .containsHorizontal(x, z)
    }

    fun isInGoalArea(config: MatchConfig, side: TeamSide, x: Double, z: Double): Boolean {
        val halfArea = goalForSide(config, side).halfArea
        return horizontalRect(halfArea.goalAreaCorner1, halfArea.goalAreaCorner2)
            .containsHorizontal(x, z)
    }

    fun isInPenaltyArc(config: MatchConfig, side: TeamSide, x: Double, z: Double): Boolean {
        if (isInPenaltyArea(config, side, x, z)) {
            return false
        }
        val goal = goalForSide(config, side)
        val spot = goal.resolvedPenaltySpot()
        val radius = goal.halfArea.penaltyArcRadius
        return horizontalDistanceSquared(x, z, spot.x, spot.z) <= radius * radius
    }

    fun isInCenterCircle(config: MatchConfig, x: Double, z: Double): Boolean {
        val center = config.kickOff
        val radius = config.centerCircleRadius
        return horizontalDistanceSquared(x, z, center.x, center.z) <= radius * radius
    }

    /**
     * 沿球场长轴、以 [goalSide] 球门为正向的一维坐标；数值越大表示越靠近该侧球门线。
     * 无法解析球场朝向时返回 null。
     */
    fun longitudinalTowardGoal(
        x: Double,
        z: Double,
        goalSide: TeamSide,
        config: MatchConfig = MatchConfigHolder.current,
    ): Double? {
        val orientation = pitchOrientation(config) ?: return null
        val goalCoord = longCoord(goalForSide(config, goalSide).goalCenter(), orientation)
        val pointCoord = longCoord(x, z, orientation)
        val towardSign = if (goalCoord >= orientation.midfieldCoord) 1.0 else -1.0
        return (pointCoord - orientation.midfieldCoord) * towardSign
    }

    fun longitudinalTowardGoal(
        player: Player,
        goalSide: TeamSide,
        config: MatchConfig = MatchConfigHolder.current,
    ): Double? = longitudinalTowardGoal(player.x, player.z, goalSide, config)

    /** [attackingTeam] 进攻方向上的纵向坐标（即靠近对方球门的坐标）。 */
    fun longitudinalTowardOpponentGoal(
        x: Double,
        z: Double,
        attackingTeam: TeamSide,
        config: MatchConfig = MatchConfigHolder.current,
    ): Double? = longitudinalTowardGoal(x, z, attackingTeam.opponent(), config)

    fun longitudinalTowardOpponentGoal(
        player: Player,
        attackingTeam: TeamSide,
        config: MatchConfig = MatchConfigHolder.current,
    ): Double? = longitudinalTowardOpponentGoal(player.x, player.z, attackingTeam, config)

    /** [attackingTeam] 是否处于对方半场。 */
    fun isInOpponentHalf(
        x: Double,
        z: Double,
        attackingTeam: TeamSide,
        config: MatchConfig = MatchConfigHolder.current,
    ): Boolean = isInHalf(config, attackingTeam.opponent(), x, z)

    fun isPlayerInOpponentHalf(
        player: Player,
        attackingTeam: TeamSide,
        config: MatchConfig = MatchConfigHolder.current,
    ): Boolean = isInOpponentHalf(player.x, player.z, attackingTeam, config)

    /** 球员是否已进入对方半场（开球前越中线判定）。 */
    fun isPlayerCrossedMidfield(
        player: Player,
        team: TeamSide,
        config: MatchConfig = MatchConfigHolder.current,
    ): Boolean = !isPlayerInHalf(player, team, config)

    fun isInCornerKickPenaltyArea(
        cornerPos: Vec3,
        x: Double,
        z: Double,
        radius: Double,
    ): Boolean = horizontalDistanceSquared(x, z, cornerPos.x, cornerPos.z) <= radius * radius

    fun isPlayerInCornerKickPenaltyArea(
        player: Player,
        cornerPos: Vec3,
        config: MatchConfig = MatchConfigHolder.current,
    ): Boolean = isInCornerKickPenaltyArea(cornerPos, player.x, player.z, config.cornerKickPenaltyAreaRadius)

    fun isInThrowInPenaltyArea(
        spot: Vec3,
        x: Double,
        z: Double,
        radius: Double,
    ): Boolean = horizontalDistanceSquared(x, z, spot.x, spot.z) <= radius * radius

    fun isPlayerInThrowInPenaltyArea(
        player: Player,
        spot: Vec3,
        config: MatchConfig = MatchConfigHolder.current,
    ): Boolean = isInThrowInPenaltyArea(spot, player.x, player.z, config.throwInPenaltyAreaRadius)

    /**
     * 将水平坐标投影到 [side] 一侧大禁区矩形边界上的最近点（用于直接任意球落点）。
     */
    fun nearestPenaltyAreaBoundary(
        side: TeamSide,
        x: Double,
        z: Double,
        config: MatchConfig = MatchConfigHolder.current,
    ): Vec3 {
        val halfArea = goalForSide(config, side).halfArea
        val rect = horizontalRect(halfArea.penaltyAreaCorner1, halfArea.penaltyAreaCorner2)
        val clampedX = x.coerceIn(rect.minX, rect.maxX)
        val clampedZ = z.coerceIn(rect.minZ, rect.maxZ)
        val inside = rect.containsHorizontal(x, z)
        if (!inside) {
            return Vec3(clampedX, config.kickOff.y, clampedZ)
        }
        val distLeft = x - rect.minX
        val distRight = rect.maxX - x
        val distBottom = z - rect.minZ
        val distTop = rect.maxZ - z
        val minDist = minOf(distLeft, distRight, distBottom, distTop)
        val y = config.kickOff.y
        return when (minDist) {
            distLeft -> Vec3(rect.minX, y, z.coerceIn(rect.minZ, rect.maxZ))
            distRight -> Vec3(rect.maxX, y, z.coerceIn(rect.minZ, rect.maxZ))
            distBottom -> Vec3(x.coerceIn(rect.minX, rect.maxX), y, rect.minZ)
            else -> Vec3(x.coerceIn(rect.minX, rect.maxX), y, rect.maxZ)
        }
    }

    private data class PitchOrientation(
        val longAxis: LongAxis,
        val midfieldCoord: Double,
    )

    private fun pitchOrientation(config: MatchConfig): PitchOrientation? {
        return when (goalLineAxis(config.goalA, config.goalB)) {
            "z" -> PitchOrientation(LongAxis.Z, config.kickOff.z)
            "x" -> PitchOrientation(LongAxis.X, config.kickOff.x)
            else -> null
        }
    }

    private fun horizontalRect(corner1: KickPosition, corner2: KickPosition): MatchFieldBounds.HorizontalRect =
        MatchFieldBounds.HorizontalRect(
            minX = minOf(corner1.x, corner2.x),
            maxX = maxOf(corner1.x, corner2.x),
            minZ = minOf(corner1.z, corner2.z),
            maxZ = maxOf(corner1.z, corner2.z),
        )

    private fun longCoord(x: Double, z: Double, orientation: PitchOrientation): Double =
        when (orientation.longAxis) {
            LongAxis.X -> x
            LongAxis.Z -> z
        }

    private fun longCoord(center: Vec3, orientation: PitchOrientation): Double =
        longCoord(center.x, center.z, orientation)

    private fun horizontalDistanceSquared(x1: Double, z1: Double, x2: Double, z2: Double): Double {
        val dx = x1 - x2
        val dz = z1 - z2
        return dx * dx + dz * dz
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
