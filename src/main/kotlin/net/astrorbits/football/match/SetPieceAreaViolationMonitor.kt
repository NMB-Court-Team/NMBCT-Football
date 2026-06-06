package net.astrorbits.football.match

import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.util.GoalkeeperUtil
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.sqrt
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SetPieceAreaViolationMonitor {
    private const val VIOLATION_TICKS = 60
    private const val REPOSITION_BUFFER = 1.0

    private data class Tracker(
        val type: SetPieceAreaViolationType,
        var ticksInArea: Int = 0,
    )

    private val trackers = ConcurrentHashMap<UUID, Tracker>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    fun clearAll(server: MinecraftServer) {
        val affected = trackers.keys.toList()
        trackers.clear()
        for (uuid in affected) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            FootballNetworking.sendSetPieceAreaViolation(player, "", 0)
        }
    }

    fun clearPlayer(player: ServerPlayer) {
        if (trackers.remove(player.uuid) != null) {
            FootballNetworking.sendSetPieceAreaViolation(player, "", 0)
        }
    }

    private fun tick(server: MinecraftServer) {
        if (!MatchState.isDuringMatch()) {
            if (trackers.isNotEmpty()) clearAll(server)
            return
        }

        val seen = mutableSetOf<UUID>()
        for (player in server.playerList.players) {
            if (!MatchParticipation.isParticipating(player)) continue
            seen.add(player.uuid)
            val violation = detectViolation(player) ?: run {
                clearPlayer(player)
                continue
            }
            val tracker = trackers[player.uuid]
            if (tracker == null || tracker.type != violation) {
                trackers[player.uuid] = Tracker(violation, ticksInArea = 1)
                syncWarning(player, violation, remainingSeconds(1))
                continue
            }
            tracker.ticksInArea++
            val remaining = remainingSeconds(tracker.ticksInArea)
            syncWarning(player, violation, remaining)
            if (tracker.ticksInArea >= VIOLATION_TICKS) {
                trackers.remove(player.uuid)
                FootballNetworking.sendSetPieceAreaViolation(player, "", 0)
                applyPenalty(server, player, violation)
            }
        }
        for (uuid in trackers.keys.toList()) {
            if (uuid !in seen) clearPlayer(server.playerList.getPlayer(uuid) ?: continue)
        }
    }

    private fun remainingSeconds(ticksInArea: Int): Int =
        ((VIOLATION_TICKS - ticksInArea).coerceAtLeast(0) + 19) / 20

    private fun syncWarning(player: ServerPlayer, type: SetPieceAreaViolationType, seconds: Int) {
        FootballNetworking.sendSetPieceAreaViolation(player, type.areaNameKey, seconds)
    }

    private fun detectViolation(player: ServerPlayer): SetPieceAreaViolationType? {
        if (SetPieceRestrictionCoordinator.isGkHoldOutsidePenaltyAreaViolation(player)) {
            return SetPieceAreaViolationType.GK_HOLD_OUTSIDE_PENALTY_AREA
        }

        val ctx = SetPieceState.active
        val team = MatchState.getPlayerTeam(player.uuid) ?: return null

        when (ctx?.kind) {
            SetPieceKind.CENTER_KICKOFF -> {
                if (MatchState.kickoffTouched) return null
                val kickoffTeam = MatchState.kickoffTeam ?: return null
                if (team != kickoffTeam && MatchFieldAreaUtil.isPlayerInCenterCircle(player)) {
                    return SetPieceAreaViolationType.KICKOFF_CENTER_CIRCLE
                }
                if (MatchFieldAreaUtil.isPlayerCrossedMidfield(player, team)) {
                    return SetPieceAreaViolationType.KICKOFF_CROSS_MIDLINE
                }
            }
            SetPieceKind.GOAL_KICK -> {
                val defending = ctx.defendingSide ?: return null
                if (team == defending.opponent() &&
                    MatchFieldAreaUtil.isPlayerInPenaltyArea(player, defending)
                ) {
                    return SetPieceAreaViolationType.GOAL_KICK_OPPONENT_IN_AREA
                }
            }
            SetPieceKind.CORNER_KICK -> {
                if (MatchState.kickoffTouched) return null
                val corner = ctx.cornerPos ?: return null
                if (team != ctx.restartTeam &&
                    MatchFieldAreaUtil.isPlayerInCornerKickPenaltyArea(player, corner)
                ) {
                    return SetPieceAreaViolationType.CORNER_KICK_OPPONENT_IN_AREA
                }
            }
            SetPieceKind.THROW_IN -> {
                if (MatchState.kickoffTouched) return null
                if (team != ctx.restartTeam &&
                    MatchFieldAreaUtil.isPlayerInThrowInPenaltyArea(player, ctx.ballPos)
                ) {
                    return SetPieceAreaViolationType.THROW_IN_OPPONENT_IN_AREA
                }
            }
            SetPieceKind.PENALTY_KICK -> {
                val defending: TeamSide
                val kickerUuid: UUID?
                when {
                    PenaltyShootoutState.isActive() -> {
                        defending = PenaltyShootoutState.activeDefendingTeam
                        kickerUuid = PenaltyShootoutState.currentKickerUuid
                    }
                    MatchPenaltyKickState.isActive() -> {
                        defending = MatchPenaltyKickState.defendingTeam
                        kickerUuid = MatchPenaltyKickState.currentKickerUuid
                    }
                    else -> return null
                }
                if (player.uuid == kickerUuid) return null
                if (PenaltyShootoutState.isActive() && PenaltyShootoutState.isDefendingGoalkeeper(player)) return null
                if (MatchPenaltyKickState.isActive() && MatchPenaltyKickState.isDefendingGoalkeeper(player)) return null
                if (MatchFieldAreaUtil.isPlayerInPenaltyArea(player, defending) ||
                    MatchFieldAreaUtil.isPlayerInPenaltyArc(player, defending)
                ) {
                    return SetPieceAreaViolationType.PENALTY_KICK_INTRUSION
                }
            }
            else -> Unit
        }
        return null
    }

    private fun applyPenalty(server: MinecraftServer, player: ServerPlayer, type: SetPieceAreaViolationType) {
        when (type) {
            SetPieceAreaViolationType.GK_HOLD_OUTSIDE_PENALTY_AREA -> {
                val team = MatchState.getPlayerTeam(player.uuid) ?: return
                val boundary = MatchFieldAreaUtil.nearestPenaltyAreaBoundary(team, player.x, player.z)
                val level = player.level()
                GoalkeeperUtil.findHeldFootball(player)?.releaseHold()
                FreeKickAwards.awardDirectFreeKick(
                    level,
                    boundary,
                    team,
                    player.uuid,
                    FreeKickFoulReason.GOALKEEPER_LEFT_PENALTY_AREA,
                )
            }
            SetPieceAreaViolationType.KICKOFF_CENTER_CIRCLE,
            SetPieceAreaViolationType.KICKOFF_CROSS_MIDLINE,
            -> {
                repositionPlayerOutsideViolationArea(player, type)
                SetPieceRestartAwards.restartCenterKickoff(server)
            }
            SetPieceAreaViolationType.GOAL_KICK_OPPONENT_IN_AREA -> {
                repositionPlayerOutsideViolationArea(player, type)
                SetPieceRestartAwards.restartGoalKick(server)
            }
            SetPieceAreaViolationType.CORNER_KICK_OPPONENT_IN_AREA -> {
                repositionPlayerOutsideViolationArea(player, type)
                SetPieceRestartAwards.restartCornerKick(server)
            }
            SetPieceAreaViolationType.THROW_IN_OPPONENT_IN_AREA -> {
                repositionPlayerOutsideViolationArea(player, type)
                SetPieceRestartAwards.restartThrowIn(server)
            }
            SetPieceAreaViolationType.PENALTY_KICK_INTRUSION -> {
                repositionPlayerOutsideViolationArea(player, type)
            }
        }
    }

    private fun repositionPlayerOutsideViolationArea(player: ServerPlayer, type: SetPieceAreaViolationType) {
        val team = MatchState.getPlayerTeam(player.uuid) ?: return
        val ctx = SetPieceState.active
        val config = MatchConfigHolder.current
        val target = when (type) {
            SetPieceAreaViolationType.KICKOFF_CENTER_CIRCLE,
            SetPieceAreaViolationType.KICKOFF_CROSS_MIDLINE,
            -> centerKickoffSafePosition(player, team, config)
            SetPieceAreaViolationType.GOAL_KICK_OPPONENT_IN_AREA ->
                ctx?.defendingSide?.let { outsidePenaltyAreaPosition(player, it, config) }
            SetPieceAreaViolationType.CORNER_KICK_OPPONENT_IN_AREA -> {
                val corner = ctx?.cornerPos ?: ctx?.ballPos
                corner?.let { outsideCirclePosition(player, it, config.cornerKickPenaltyAreaRadius, config) }
            }
            SetPieceAreaViolationType.THROW_IN_OPPONENT_IN_AREA ->
                ctx?.ballPos?.let { outsideCirclePosition(player, it, config.throwInPenaltyAreaRadius, config) }
            SetPieceAreaViolationType.PENALTY_KICK_INTRUSION ->
                penaltyKickSafePosition(player, config)
            SetPieceAreaViolationType.GK_HOLD_OUTSIDE_PENALTY_AREA -> null
        } ?: return

        teleportHorizontally(player, target)
    }

    private fun centerKickoffSafePosition(
        player: ServerPlayer,
        team: TeamSide,
        config: MatchConfig,
    ): Vec3 {
        val center = config.kickOff
        val orientation = pitchOrientation(config) ?: return outsideCirclePosition(
            player,
            Vec3(center.x, player.y, center.z),
            config.centerCircleRadius,
            config,
        )
        val teamGoalCoord = longCoord(MatchFieldAreaUtil.goalForSide(config, team).goalCenter(), orientation)
        val ownHalfSign = if (teamGoalCoord >= orientation.midfieldCoord) 1.0 else -1.0
        val safeLongCoord = orientation.midfieldCoord + ownHalfSign * (config.centerCircleRadius + REPOSITION_BUFFER)
        return when (orientation.longAxis) {
            LongAxis.X -> Vec3(safeLongCoord, player.y, center.z)
            LongAxis.Z -> Vec3(center.x, player.y, safeLongCoord)
        }
    }

    private fun outsidePenaltyAreaPosition(
        player: ServerPlayer,
        side: TeamSide,
        config: MatchConfig,
    ): Vec3 {
        val rect = penaltyAreaRect(side, config)
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
            distLeft -> Vec3(rect.minX - REPOSITION_BUFFER, player.y, clampedZ)
            distRight -> Vec3(rect.maxX + REPOSITION_BUFFER, player.y, clampedZ)
            distBottom -> Vec3(clampedX, player.y, rect.minZ - REPOSITION_BUFFER)
            else -> Vec3(clampedX, player.y, rect.maxZ + REPOSITION_BUFFER)
        }
    }

    private fun outsideCirclePosition(
        player: ServerPlayer,
        center: Vec3,
        radius: Double,
        config: MatchConfig,
    ): Vec3 {
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
        val distance = radius + REPOSITION_BUFFER
        return Vec3(
            center.x + dx / length * distance,
            player.y,
            center.z + dz / length * distance,
        )
    }

    private fun penaltyKickSafePosition(player: ServerPlayer, config: MatchConfig): Vec3? {
        val defending = when {
            PenaltyShootoutState.isActive() -> PenaltyShootoutState.activeDefendingTeam
            MatchPenaltyKickState.isActive() -> MatchPenaltyKickState.defendingTeam
            else -> return null
        }
        if (MatchFieldAreaUtil.isPlayerInPenaltyArea(player, defending, config)) {
            return outsidePenaltyAreaPosition(player, defending, config)
        }
        if (MatchFieldAreaUtil.isPlayerInPenaltyArc(player, defending, config)) {
            val goal = MatchFieldAreaUtil.goalForSide(config, defending)
            val spot = goal.resolvedPenaltySpot()
            return outsideCirclePosition(player, Vec3(spot.x, player.y, spot.z), goal.halfArea.penaltyArcRadius, config)
        }
        return null
    }

    private fun teleportHorizontally(player: ServerPlayer, target: Vec3) {
        val level = player.level()
        player.teleportTo(
            level,
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

    private fun penaltyAreaRect(side: TeamSide, config: MatchConfig): MatchFieldBounds.HorizontalRect {
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
