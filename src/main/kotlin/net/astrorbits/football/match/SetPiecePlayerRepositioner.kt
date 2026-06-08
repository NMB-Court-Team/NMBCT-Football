package net.astrorbits.football.match

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 定位球状态建立后，立即把已经站在新禁入区内的球员移出，避免复位足球后无意义地触发犯规。
 */
object SetPiecePlayerRepositioner {
    private const val BUFFER = 1.0

    fun repositionInitialViolators(server: MinecraftServer, context: SetPieceContext) {
        for (player in server.playerList.players) {
            if (PenaltyShootoutState.isActive() && PenaltyShootoutState.isPenaltyWaitingSpectator(player)) continue
            if (!MatchParticipation.isParticipating(player)) continue
            val team = MatchState.getPlayerTeam(player.uuid) ?: continue
            val target = when (context.kind) {
                SetPieceKind.CENTER_KICKOFF -> centerKickoffTarget(player, team, context)
                SetPieceKind.GOAL_KICK -> goalKickTarget(player, team, context)
                SetPieceKind.CORNER_KICK -> cornerKickTarget(player, team, context)
                SetPieceKind.THROW_IN -> throwInTarget(player, team, context)
                SetPieceKind.PENALTY_KICK -> penaltyKickTarget(player, context)
                SetPieceKind.FREE_KICK -> freeKickTarget(player, team, context)
                SetPieceKind.NONE -> null
            } ?: continue
            teleportHorizontally(player, target)
        }
    }

    private fun centerKickoffTarget(player: ServerPlayer, team: TeamSide, context: SetPieceContext): Vec3? {
        if (MatchState.kickoffTouched) return null
        val crossedMidfield = MatchFieldAreaUtil.isPlayerCrossedMidfield(player, team)
        val inCenterCircle = team != context.restartTeam && MatchFieldAreaUtil.isPlayerInCenterCircle(player)
        if (!crossedMidfield && !inCenterCircle) return null
        return centerKickoffSafePosition(player, team)
    }

    private fun goalKickTarget(player: ServerPlayer, team: TeamSide, context: SetPieceContext): Vec3? {
        val defending = context.defendingSide ?: return null
        if (team != defending.opponent()) return null
        if (!MatchFieldAreaUtil.isPlayerInPenaltyArea(player, defending)) return null
        return outsidePenaltyAreaPosition(player, defending)
    }

    private fun cornerKickTarget(player: ServerPlayer, team: TeamSide, context: SetPieceContext): Vec3? {
        if (player.uuid == context.cornerKickTakerUuid) return null
        if (MatchState.kickoffTouched || team == context.restartTeam) return null
        val corner = context.cornerPos ?: context.ballPos
        if (!MatchFieldAreaUtil.isPlayerInCornerKickPenaltyArea(player, corner)) return null
        return outsideCirclePosition(player, corner, MatchConfigHolder.current.cornerKickPenaltyAreaRadius)
    }

    private fun throwInTarget(player: ServerPlayer, team: TeamSide, context: SetPieceContext): Vec3? {
        if (MatchState.kickoffTouched || team == context.restartTeam) return null
        if (!MatchFieldAreaUtil.isPlayerInThrowInPenaltyArea(player, context.ballPos)) return null
        return outsideCirclePosition(player, context.ballPos, MatchConfigHolder.current.throwInPenaltyAreaRadius)
    }

    private fun penaltyKickTarget(player: ServerPlayer, context: SetPieceContext): Vec3? {
        val defending = when {
            MatchPenaltyKickState.isActive() -> MatchPenaltyKickState.defendingTeam
            PenaltyShootoutState.isActive() -> PenaltyShootoutState.penaltyGoalTeam
            else -> context.defendingSide
        } ?: return null
        val kickerUuid = when {
            MatchPenaltyKickState.isActive() -> MatchPenaltyKickState.currentKickerUuid
            PenaltyShootoutState.isActive() -> PenaltyShootoutState.currentKickerUuid
            else -> null
        }
        if (player.uuid == kickerUuid) return null
        if (MatchPenaltyKickState.isActive() && MatchPenaltyKickState.isDefendingGoalkeeper(player)) return null
        if (PenaltyShootoutState.isActive() && PenaltyShootoutState.isDefendingGoalkeeper(player)) return null
        if (MatchFieldAreaUtil.isPlayerInPenaltyArea(player, defending)) {
            return outsidePenaltyAreaPosition(player, defending)
        }
        if (MatchFieldAreaUtil.isPlayerInPenaltyArc(player, defending)) {
            val goal = MatchFieldAreaUtil.goalForSide(MatchConfigHolder.current, defending)
            val spot = goal.resolvedPenaltySpot()
            return outsideCirclePosition(player, Vec3(spot.x, player.y, spot.z), goal.halfArea.penaltyArcRadius)
        }
        return null
    }

    private fun freeKickTarget(player: ServerPlayer, team: TeamSide, context: SetPieceContext): Vec3? {
        if (MatchState.kickoffTouched) return null
        if (team == context.restartTeam) return null
        if (player.uuid == context.freeKickTakerUuid) return null

        val ballPos = context.ballPos
        val config = MatchConfigHolder.current
        val ballPaSide = MatchFieldAreaUtil.penaltyAreaSideContainingBall(ballPos, config)
        if (ballPaSide != null &&
            team == ballPaSide &&
            MatchFieldAreaUtil.isPlayerInPenaltyArea(player, ballPaSide, config)
        ) {
            return outsidePenaltyAreaPosition(player, ballPaSide)
        }
        if (MatchFieldAreaUtil.isPlayerWithinFreeKickDistance(player, ballPos, config)) {
            return outsideCirclePosition(player, ballPos, config.freeKickDistanceRadius)
        }
        return null
    }

    private fun centerKickoffSafePosition(player: ServerPlayer, team: TeamSide): Vec3 {
        val config = MatchConfigHolder.current
        val center = config.kickOff
        val orientation = pitchOrientation(config) ?: return outsideCirclePosition(
            player,
            Vec3(center.x, player.y, center.z),
            config.centerCircleRadius,
        )
        val teamGoalCoord = longCoord(MatchFieldAreaUtil.goalForSide(config, team).goalCenter(), orientation)
        val ownHalfSign = if (teamGoalCoord >= orientation.midfieldCoord) 1.0 else -1.0
        val safeLongCoord = orientation.midfieldCoord + ownHalfSign * (config.centerCircleRadius + BUFFER)
        return when (orientation.longAxis) {
            LongAxis.X -> Vec3(safeLongCoord, player.y, center.z)
            LongAxis.Z -> Vec3(center.x, player.y, safeLongCoord)
        }
    }

    private fun outsidePenaltyAreaPosition(player: ServerPlayer, side: TeamSide): Vec3 {
        val rect = penaltyAreaRect(side)
        val x = player.x
        val z = player.z
        if (!rect.containsHorizontal(x, z)) {
            return Vec3(x, player.y, z)
        }

        val distLeft = x - rect.minX
        val distRight = rect.maxX - x
        val distBottom = z - rect.minZ
        val distTop = rect.maxZ - z
        val minDist = minOf(distLeft, distRight, distBottom, distTop)
        val clampedX = x.coerceIn(rect.minX, rect.maxX)
        val clampedZ = z.coerceIn(rect.minZ, rect.maxZ)

        return when (minDist) {
            distLeft -> Vec3(rect.minX - BUFFER, player.y, clampedZ)
            distRight -> Vec3(rect.maxX + BUFFER, player.y, clampedZ)
            distBottom -> Vec3(clampedX, player.y, rect.minZ - BUFFER)
            else -> Vec3(clampedX, player.y, rect.maxZ + BUFFER)
        }
    }

    private fun outsideCirclePosition(player: ServerPlayer, center: Vec3, radius: Double): Vec3 {
        val config = MatchConfigHolder.current
        var dx = player.x - center.x
        var dz = player.z - center.z
        if (dx * dx + dz * dz < 1.0E-6) {
            dx = config.kickOff.x - center.x
            dz = config.kickOff.z - center.z
        }
        if (dx * dx + dz * dz < 1.0E-6) {
            dx = 1.0
            dz = 0.0
        }
        val length = sqrt(dx * dx + dz * dz)
        val distance = radius + BUFFER
        return Vec3(
            center.x + dx / length * distance,
            player.y,
            center.z + dz / length * distance,
        )
    }

    private fun teleportHorizontally(player: ServerPlayer, target: Vec3) {
        player.teleportTo(
            player.level(),
            target.x,
            target.y,
            target.z,
            java.util.HashSet(),
            player.yRot,
            player.xRot,
            false,
        )
        player.setDeltaMovement(Vec3.ZERO)
    }

    private fun penaltyAreaRect(side: TeamSide): MatchFieldBounds.HorizontalRect {
        val halfArea = MatchFieldAreaUtil.goalForSide(MatchConfigHolder.current, side).halfArea
        return horizontalRect(halfArea.penaltyAreaCorner1, halfArea.penaltyAreaCorner2)
    }

    private fun horizontalRect(corner1: KickPosition, corner2: KickPosition): MatchFieldBounds.HorizontalRect =
        MatchFieldBounds.HorizontalRect(
            minX = minOf(corner1.x, corner2.x),
            maxX = maxOf(corner1.x, corner2.x),
            minZ = minOf(corner1.z, corner2.z),
            maxZ = maxOf(corner1.z, corner2.z),
        )

    private enum class LongAxis { X, Z }

    private data class PitchOrientation(
        val longAxis: LongAxis,
        val midfieldCoord: Double,
    )

    private fun pitchOrientation(config: MatchConfig): PitchOrientation? {
        val epsilon = 1e-3
        val constantZ = abs(config.goalA.z1 - config.goalA.z2) < epsilon &&
            abs(config.goalB.z1 - config.goalB.z2) < epsilon
        val constantX = abs(config.goalA.x1 - config.goalA.x2) < epsilon &&
            abs(config.goalB.x1 - config.goalB.x2) < epsilon
        return when {
            constantZ -> PitchOrientation(LongAxis.Z, config.kickOff.z)
            constantX -> PitchOrientation(LongAxis.X, config.kickOff.x)
            else -> null
        }
    }

    private fun longCoord(center: Vec3, orientation: PitchOrientation): Double =
        when (orientation.longAxis) {
            LongAxis.X -> center.x
            LongAxis.Z -> center.z
        }
}
