package net.astrorbits.football.match

import net.minecraft.world.phys.Vec3
import kotlin.math.*

/**
 * 定位球期间「球员不应进入」的区域边界，用于客户端近距提示粒子。
 *
 * - [InteriorForbidden]：禁止进入区域内部（如中圈、对方大禁区）。
 * - [ExteriorForbidden]：禁止离开区域外部（如守门员须留在大禁区内）。
 */
sealed interface ForbiddenZone {
    fun proximityToForbidden(x: Double, z: Double, config: MatchConfig = MatchConfigHolder.current): Double

    fun shouldShowOutline(
        x: Double,
        z: Double,
        threshold: Double = PROXIMITY_THRESHOLD,
        config: MatchConfig = MatchConfigHolder.current,
    ): Boolean = proximityToForbidden(x, z, config) <= threshold

    fun sampleBoundaryPoints(
        config: MatchConfig = MatchConfigHolder.current,
        spacing: Double = BOUNDARY_POINT_SPACING,
    ): List<Vec3>

    companion object {
        const val PROXIMITY_THRESHOLD = 6.0
        const val BOUNDARY_POINT_SPACING = 0.75
    }
}

/** 不得进入区域内部。 */
data class InteriorForbiddenCircle(
    val centerX: Double,
    val centerZ: Double,
    val radius: Double,
) : ForbiddenZone {
    override fun proximityToForbidden(x: Double, z: Double, config: MatchConfig): Double {
        val dist = horizontalDistance(x, z, centerX, centerZ)
        return if (dist <= radius) 0.0 else dist - radius
    }

    override fun sampleBoundaryPoints(config: MatchConfig, spacing: Double): List<Vec3> {
        val circumference = 2.0 * PI * radius
        val steps = pointCount(circumference, spacing)
        val y = referenceY(config)
        return (0 until steps).map { i ->
            val angle = 2.0 * PI * i / steps
            Vec3(centerX + cos(angle) * radius, y, centerZ + sin(angle) * radius)
        }
    }
}

/** 不得进入 [side] 一侧大禁区内部。 */
data class InteriorForbiddenPenaltyArea(val side: TeamSide) : ForbiddenZone {
    override fun proximityToForbidden(x: Double, z: Double, config: MatchConfig): Double =
        distanceToInteriorRect(x, z, penaltyRect(config, side))

    override fun sampleBoundaryPoints(config: MatchConfig, spacing: Double): List<Vec3> =
        sampleRectBoundary(penaltyRect(config, side), referenceY(config), spacing)
}

/** 不得越过中线进入对方半场。 */
data class InteriorForbiddenOpponentHalf(val team: TeamSide) : ForbiddenZone {
    override fun proximityToForbidden(x: Double, z: Double, config: MatchConfig): Double {
        if (MatchFieldAreaUtil.isInOpponentHalf(x, z, team, config)) {
            return 0.0
        }
        return distanceToMidfieldLine(x, z, config)
    }

    override fun sampleBoundaryPoints(config: MatchConfig, spacing: Double): List<Vec3> {
        val orientation = pitchOrientation(config) ?: return emptyList()
        val pitch = MatchFieldBounds.pitchRect(config) ?: return emptyList()
        val y = referenceY(config)
        return when (orientation.longAxis) {
            LongAxis.Z -> sampleLineSegment(
                pitch.minX, orientation.midfieldCoord,
                pitch.maxX, orientation.midfieldCoord,
                y,
                spacing,
            )
            LongAxis.X -> sampleLineSegment(
                orientation.midfieldCoord, pitch.minZ,
                orientation.midfieldCoord, pitch.maxZ,
                y,
                spacing,
            )
        }
    }
}

/** 点球大战：不得进入防守方大禁区或罚球弧。 */
data class InteriorForbiddenPenaltyKickZone(val defendingSide: TeamSide) : ForbiddenZone {
    override fun proximityToForbidden(x: Double, z: Double, config: MatchConfig): Double {
        if (MatchFieldAreaUtil.isInPenaltyArea(config, defendingSide, x, z)) {
            return 0.0
        }
        if (MatchFieldAreaUtil.isInPenaltyArc(config, defendingSide, x, z)) {
            return 0.0
        }
        val rectDist = distanceToInteriorRect(x, z, penaltyRect(config, defendingSide))
        val goal = MatchFieldAreaUtil.goalForSide(config, defendingSide)
        val spot = goal.resolvedPenaltySpot()
        val arcRadius = goal.halfArea.penaltyArcRadius
        val circleDist = distanceToInteriorCircle(x, z, spot.x, spot.z, arcRadius)
        return min(rectDist, circleDist)
    }

    override fun sampleBoundaryPoints(config: MatchConfig, spacing: Double): List<Vec3> {
        val y = referenceY(config)
        val rectPoints = sampleRectBoundary(penaltyRect(config, defendingSide), y, spacing)
        val goal = MatchFieldAreaUtil.goalForSide(config, defendingSide)
        val spot = goal.resolvedPenaltySpot()
        val arcRadius = goal.halfArea.penaltyArcRadius
        val circumference = 2.0 * PI * arcRadius
        val steps = pointCount(circumference, spacing)
        val arcPoints = (0 until steps).mapNotNull { i ->
            val angle = 2.0 * PI * i / steps
            val px = spot.x + cos(angle) * arcRadius
            val pz = spot.z + sin(angle) * arcRadius
            if (MatchFieldAreaUtil.isInPenaltyArea(config, defendingSide, px, pz)) {
                null
            } else {
                Vec3(px, y, pz)
            }
        }
        return rectPoints + arcPoints
    }
}

/** 守门员持球须留在大禁区内：禁止处于禁区外；在禁区内接近边线时也提示。 */
data class ExteriorForbiddenPenaltyArea(val side: TeamSide) : ForbiddenZone {
    override fun proximityToForbidden(x: Double, z: Double, config: MatchConfig): Double {
        val rect = penaltyRect(config, side)
        if (!rect.containsHorizontal(x, z)) {
            return 0.0
        }
        return distanceToRectEdgeFromInside(x, z, rect)
    }

    override fun sampleBoundaryPoints(config: MatchConfig, spacing: Double): List<Vec3> =
        sampleRectBoundary(penaltyRect(config, side), referenceY(config), spacing)
}

object SetPieceForbiddenZoneResolver {
    fun resolve(
        playerX: Double,
        playerZ: Double,
        playerUuid: java.util.UUID,
        playerTeam: TeamSide,
        isGoalkeeper: Boolean,
        isHoldingBall: Boolean,
        kickoffTouched: Boolean,
        kickoffTeam: TeamSide?,
        setPieceKind: SetPieceKind,
        restartTeam: TeamSide?,
        defendingSide: TeamSide?,
        ballPos: Vec3?,
        cornerPos: Vec3?,
        penaltyShootoutActive: Boolean,
        penaltyKickerUuid: java.util.UUID?,
        penaltyDefendingTeam: TeamSide?,
        isDefendingGoalkeeper: Boolean,
        config: MatchConfig = MatchConfigHolder.current,
    ): List<ForbiddenZone> {
        val zones = mutableListOf<ForbiddenZone>()

        if (isGoalkeeper && isHoldingBall &&
            !MatchFieldAreaUtil.isInPenaltyArea(config, playerTeam, playerX, playerZ)
        ) {
            zones += ExteriorForbiddenPenaltyArea(playerTeam)
        } else if (isGoalkeeper && isHoldingBall) {
            val rect = penaltyRect(config, playerTeam)
            if (rect.containsHorizontal(playerX, playerZ)) {
                val edgeDist = distanceToRectEdgeFromInside(playerX, playerZ, rect)
                if (edgeDist <= ForbiddenZone.PROXIMITY_THRESHOLD) {
                    zones += ExteriorForbiddenPenaltyArea(playerTeam)
                }
            }
        }

        when (setPieceKind) {
            SetPieceKind.CENTER_KICKOFF -> {
                if (!kickoffTouched) {
                    // 当前定位球状态比通用开球缓存更新，半场切换等场景必须优先使用 restartTeam。
                    val kickoff = restartTeam ?: kickoffTeam
                    if (kickoff != null && playerTeam != kickoff) {
                        val center = config.kickOff
                        zones += InteriorForbiddenCircle(center.x, center.z, config.centerCircleRadius)
                    }
                    zones += InteriorForbiddenOpponentHalf(playerTeam)
                }
            }
            SetPieceKind.GOAL_KICK -> {
                val defending = defendingSide ?: return zones
                if (playerTeam == defending.opponent()) {
                    zones += InteriorForbiddenPenaltyArea(defending)
                }
            }
            SetPieceKind.CORNER_KICK -> {
                if (!kickoffTouched && playerTeam != restartTeam) {
                    val corner = cornerPos ?: ballPos ?: return zones
                    zones += InteriorForbiddenCircle(
                        corner.x,
                        corner.z,
                        config.cornerKickPenaltyAreaRadius,
                    )
                }
            }
            SetPieceKind.THROW_IN -> {
                if (!kickoffTouched && playerTeam != restartTeam) {
                    val spot = ballPos ?: return zones
                    zones += InteriorForbiddenCircle(
                        spot.x,
                        spot.z,
                        config.throwInPenaltyAreaRadius,
                    )
                }
            }
            SetPieceKind.PENALTY_KICK -> {
                if (playerUuid == penaltyKickerUuid) return zones
                if (isDefendingGoalkeeper) return zones
                val defending = penaltyDefendingTeam ?: defendingSide ?: return zones
                zones += InteriorForbiddenPenaltyKickZone(defending)
            }
            SetPieceKind.FREE_KICK -> {
                if (kickoffTouched || playerTeam == restartTeam) return zones
                val spot = ballPos ?: return zones
                val ballPaSide = MatchFieldAreaUtil.penaltyAreaSideContainingBall(spot, config)
                if (ballPaSide != null && playerTeam == ballPaSide) {
                    zones += InteriorForbiddenPenaltyArea(ballPaSide)
                }
                zones += InteriorForbiddenCircle(
                    spot.x,
                    spot.z,
                    config.freeKickDistanceRadius,
                )
            }
            else -> Unit
        }

        return zones.filter { it.shouldShowOutline(playerX, playerZ, config = config) }
    }
}

private enum class LongAxis { X, Z }

private data class PitchOrientation(
    val longAxis: LongAxis,
    val midfieldCoord: Double,
)

private fun referenceY(config: MatchConfig): Double = config.kickOff.y

private fun penaltyRect(config: MatchConfig, side: TeamSide): MatchFieldBounds.HorizontalRect {
    val halfArea = MatchFieldAreaUtil.goalForSide(config, side).halfArea
    return horizontalRect(halfArea.penaltyAreaCorner1, halfArea.penaltyAreaCorner2)
}

private fun horizontalRect(corner1: KickPosition, corner2: KickPosition): MatchFieldBounds.HorizontalRect =
    MatchFieldBounds.HorizontalRect(
        minX = minOf(corner1.x, corner2.x),
        maxX = maxOf(corner1.x, corner2.x),
        minZ = minOf(corner1.z, corner2.z),
        maxZ = maxOf(corner1.z, corner2.z),
    )

private fun horizontalDistance(x1: Double, z1: Double, x2: Double, z2: Double): Double {
    val dx = x1 - x2
    val dz = z1 - z2
    return sqrt(dx * dx + dz * dz)
}

private fun distanceToInteriorRect(
    x: Double,
    z: Double,
    rect: MatchFieldBounds.HorizontalRect,
): Double {
    if (rect.containsHorizontal(x, z)) {
        return 0.0
    }
    val dx = when {
        x < rect.minX -> rect.minX - x
        x > rect.maxX -> x - rect.maxX
        else -> 0.0
    }
    val dz = when {
        z < rect.minZ -> rect.minZ - z
        z > rect.maxZ -> z - rect.maxZ
        else -> 0.0
    }
    return sqrt(dx * dx + dz * dz)
}

private fun distanceToInteriorCircle(
    x: Double,
    z: Double,
    cx: Double,
    cz: Double,
    radius: Double,
): Double {
    val dist = horizontalDistance(x, z, cx, cz)
    return if (dist <= radius) 0.0 else dist - radius
}

private fun distanceToRectEdgeFromInside(
    x: Double,
    z: Double,
    rect: MatchFieldBounds.HorizontalRect,
): Double =
    min(
        min(x - rect.minX, rect.maxX - x),
        min(z - rect.minZ, rect.maxZ - z),
    )

private fun distanceToMidfieldLine(x: Double, z: Double, config: MatchConfig): Double {
    val orientation = pitchOrientation(config) ?: return Double.MAX_VALUE
    return when (orientation.longAxis) {
        LongAxis.Z -> abs(z - orientation.midfieldCoord)
        LongAxis.X -> abs(x - orientation.midfieldCoord)
    }
}

private fun pitchOrientation(config: MatchConfig): PitchOrientation? {
    val goalA = config.goalA
    val goalB = config.goalB
    val epsilon = 1e-3
    val constantZ = abs(goalA.z1 - goalA.z2) < epsilon && abs(goalB.z1 - goalB.z2) < epsilon
    val constantX = abs(goalA.x1 - goalA.x2) < epsilon && abs(goalB.x1 - goalB.x2) < epsilon
    return when {
        constantZ -> PitchOrientation(LongAxis.Z, config.kickOff.z)
        constantX -> PitchOrientation(LongAxis.X, config.kickOff.x)
        else -> null
    }
}

private fun pointCount(length: Double, spacing: Double): Int =
    maxOf(8, (length / spacing).toInt())

private fun sampleRectBoundary(
    rect: MatchFieldBounds.HorizontalRect,
    y: Double,
    spacing: Double,
): List<Vec3> {
    val points = mutableListOf<Vec3>()
    val width = rect.maxX - rect.minX
    val depth = rect.maxZ - rect.minZ
    points += sampleLineSegment(rect.minX, rect.minZ, rect.maxX, rect.minZ, y, spacing)
    points += sampleLineSegment(rect.maxX, rect.minZ, rect.maxX, rect.maxZ, y, spacing)
    points += sampleLineSegment(rect.maxX, rect.maxZ, rect.minX, rect.maxZ, y, spacing)
    points += sampleLineSegment(rect.minX, rect.maxZ, rect.minX, rect.minZ, y, spacing)
    if (width < 1e-3 || depth < 1e-3) {
        return points.distinctBy { "${it.x},${it.z}" }
    }
    return points
}

private fun sampleLineSegment(
    x1: Double,
    z1: Double,
    x2: Double,
    z2: Double,
    y: Double,
    spacing: Double,
): List<Vec3> {
    val dist = horizontalDistance(x1, z1, x2, z2)
    val steps = pointCount(dist, spacing)
    return (0..steps).map { i ->
        val t = i.toDouble() / steps
        Vec3(x1 + (x2 - x1) * t, y, z1 + (z2 - z1) * t)
    }
}
