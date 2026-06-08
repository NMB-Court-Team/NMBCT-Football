package net.astrorbits.football.match

import net.astrorbits.football.Football
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.util.GoalkeeperUtil
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sqrt

object SetPieceAreaViolationMonitor {
    private const val VIOLATION_TICKS = 60
    private const val REPOSITION_BUFFER = 1.0
    private const val STATIONARY_SPEED_SQR = 0.002
    private const val STATIONARY_TICKS_NEEDED = 20

    private var goalKickAwaitingExitStationaryTicks = 0
    private var freeKickBallViolationTicks = 0

    private data class Tracker(
        val type: SetPieceAreaViolationType,
        var ticksInArea: Int = 0,
    )

    private val trackers = ConcurrentHashMap<UUID, Tracker>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    fun clearAll(server: MinecraftServer) {
        resetBallViolationTicks()
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
        tickBallViolations(server)
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
                if (ctx.goalKickPhase == GoalKickPhase.AWAITING_PA_EXIT) return null
                val defending = ctx.defendingSide ?: return null
                if (!MatchState.kickoffTouched &&
                    team == defending.opponent() &&
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
                if (PenaltyShootoutState.isActive() && PenaltyShootoutState.isPenaltyWaitingSpectator(player)) {
                    return null
                }
                val defending: TeamSide
                val kickerUuid: UUID?
                val ballPos: Vec3
                val kickerX: Double
                val kickerZ: Double
                when {
                    PenaltyShootoutState.isActive() -> {
                        defending = PenaltyShootoutState.penaltyGoalTeam
                        kickerUuid = PenaltyShootoutState.currentKickerUuid
                        ballPos = ctx.ballPos
                        val kicker = kickerUuid?.let { player.level().server.playerList.getPlayer(it) }
                        kickerX = kicker?.x ?: ballPos.x
                        kickerZ = kicker?.z ?: ballPos.z
                    }
                    MatchPenaltyKickState.isActive() -> {
                        defending = MatchPenaltyKickState.defendingTeam
                        kickerUuid = MatchPenaltyKickState.currentKickerUuid
                        ballPos = ctx.ballPos
                        val kicker = kickerUuid?.let { player.level().server.playerList.getPlayer(it) }
                        kickerX = kicker?.x ?: ballPos.x
                        kickerZ = kicker?.z ?: ballPos.z
                    }
                    else -> return null
                }
                if (player.uuid == kickerUuid) return null
                if (PenaltyShootoutState.isActive() && PenaltyShootoutState.isDefendingGoalkeeper(player)) return null
                if (MatchPenaltyKickState.isActive() && MatchPenaltyKickState.isDefendingGoalkeeper(player)) return null
                if (!MatchFieldAreaUtil.isPlayerInValidPenaltyKickStandingZone(
                        player,
                        ballPos,
                        kickerX,
                        kickerZ,
                        defending,
                    )
                ) {
                    return SetPieceAreaViolationType.PENALTY_KICK_INTRUSION
                }
            }
            SetPieceKind.FREE_KICK -> {
                if (MatchState.kickoffTouched) return null
                val ballPos = ctx.ballPos
                val restartTeam = ctx.restartTeam
                val config = MatchConfigHolder.current
                if (team == restartTeam) return null
                val ballPaSide = MatchFieldAreaUtil.penaltyAreaSideContainingBall(ballPos, config)
                // 球在发球方己方禁区：对方不得进入该禁区（攻方在守方禁区罚球不适用此条）
                if (ballPaSide == restartTeam &&
                    MatchFieldAreaUtil.isPlayerInPenaltyArea(player, ballPaSide, config)
                ) {
                    return SetPieceAreaViolationType.FREE_KICK_OPPONENT_IN_ATTACK_PA
                }
                if (MatchFieldAreaUtil.isPlayerWithinFreeKickDistance(player, ballPos, config)) {
                    return SetPieceAreaViolationType.FREE_KICK_TOO_CLOSE
                }
            }
            else -> Unit
        }
        return null
    }

    private fun tickBallViolations(server: MinecraftServer) {
        if (!MatchState.isDuringMatch()) {
            resetBallViolationTicks()
            return
        }
        val level = server.overworld()
        val ball = GoalKickSetPieceFlow.findMatchFootball(level) ?: run {
            resetBallViolationTicks()
            return
        }
        val ballPos = Vec3(ball.x, ball.y, ball.z)
        val ctx = SetPieceState.active
        if (ctx?.kind == SetPieceKind.GOAL_KICK && ctx.goalKickPhase == GoalKickPhase.AWAITING_PA_EXIT) {
            tickGoalKickAwaitingPenaltyAreaExit(server, ball, ballPos, ctx)
            return
        }
        if (!MatchState.kickoffTouched) {
            resetBallViolationTicks()
            return
        }
        if (ctx == null) {
            resetBallViolationTicks()
            return
        }
        when (ctx.kind) {
            SetPieceKind.GOAL_KICK -> Unit
            SetPieceKind.FREE_KICK -> {
                val paSide = MatchFieldAreaUtil.penaltyAreaSideContainingBall(ctx.ballPos)
                if (paSide != ctx.restartTeam) {
                    freeKickBallViolationTicks = 0
                    return
                }
                if (MatchFieldAreaUtil.isBallInPenaltyArea(paSide, ballPos)) {
                    freeKickBallViolationTicks++
                    if (freeKickBallViolationTicks >= VIOLATION_TICKS) {
                        freeKickBallViolationTicks = 0
                        SetPieceRestartAwards.restartFreeKick(
                            server,
                            SetPieceRestartCause.ballOnly(SetPieceRestartReasonKeys.FREE_KICK_BALL_IN_AREA),
                        )
                    }
                } else {
                    freeKickBallViolationTicks = 0
                }
            }
            else -> resetBallViolationTicks()
        }
    }

    private fun tickGoalKickAwaitingPenaltyAreaExit(
        server: MinecraftServer,
        ball: Football,
        ballPos: Vec3,
        ctx: SetPieceContext,
    ) {
        val defending = GoalKickSetPieceFlow.penaltyAreaSide(ctx)
        if (!MatchFieldAreaUtil.isBallInPenaltyArea(defending, ballPos)) {
            goalKickAwaitingExitStationaryTicks = 0
            GoalKickSetPieceFlow.completeAwaitingPaExit(server, ball)
            return
        }
        if (ball.simulationVelocity().lengthSqr() < STATIONARY_SPEED_SQR) {
            goalKickAwaitingExitStationaryTicks++
            if (goalKickAwaitingExitStationaryTicks >= STATIONARY_TICKS_NEEDED) {
                goalKickAwaitingExitStationaryTicks = 0
                ball.releaseHold()
                SetPieceRestartAwards.restartGoalKick(
                    server,
                    SetPieceRestartCause.ballOnly(SetPieceRestartReasonKeys.GOAL_KICK_BALL_STATIONARY),
                )
            }
        } else {
            goalKickAwaitingExitStationaryTicks = 0
        }
    }

    private fun resetBallViolationTicks() {
        goalKickAwaitingExitStationaryTicks = 0
        freeKickBallViolationTicks = 0
    }

    private fun applyPenalty(server: MinecraftServer, player: ServerPlayer, type: SetPieceAreaViolationType) {
        when (type) {
            SetPieceAreaViolationType.GK_HOLD_OUTSIDE_PENALTY_AREA -> {
                val team = MatchState.getPlayerTeam(player.uuid) ?: return
                val level = player.level()
                val football = GoalkeeperUtil.findHeldFootball(player)
                val foulPos = if (football != null) {
                    Vec3(football.x, football.y, football.z)
                } else {
                    Vec3(player.x, player.y, player.z)
                }
                football?.releaseHold()
                FreeKickAwards.awardDirectFreeKick(
                    level,
                    foulPos,
                    team,
                    player.uuid,
                    FreeKickFoulReason.GOALKEEPER_LEFT_PENALTY_AREA,
                )
            }
            SetPieceAreaViolationType.KICKOFF_CENTER_CIRCLE,
            SetPieceAreaViolationType.KICKOFF_CROSS_MIDLINE,
            -> {
                repositionPlayerOutsideViolationArea(player, type)
                SetPieceRestartAwards.restartCenterKickoff(
                    server,
                    SetPieceRestartCause.fromViolation(player, type),
                )
            }
            SetPieceAreaViolationType.GOAL_KICK_OPPONENT_IN_AREA -> {
                repositionPlayerOutsideViolationArea(player, type)
                SetPieceRestartAwards.restartGoalKick(
                    server,
                    SetPieceRestartCause.fromViolation(player, type),
                )
            }
            SetPieceAreaViolationType.CORNER_KICK_OPPONENT_IN_AREA -> {
                repositionPlayerOutsideViolationArea(player, type)
                SetPieceRestartAwards.restartCornerKick(
                    server,
                    SetPieceRestartCause.fromViolation(player, type),
                )
            }
            SetPieceAreaViolationType.THROW_IN_OPPONENT_IN_AREA -> {
                repositionPlayerOutsideViolationArea(player, type)
                SetPieceRestartAwards.restartThrowIn(
                    server,
                    SetPieceRestartCause.fromViolation(player, type),
                )
            }
            SetPieceAreaViolationType.PENALTY_KICK_INTRUSION -> {
                if (MatchPenaltyKickState.isActive()) {
                    SetPieceRestartAwards.restartPenaltyKick(
                        server,
                        SetPieceRestartCause.fromViolation(player, type),
                    )
                } else {
                    repositionPlayerOutsideViolationArea(player, type)
                }
            }
            SetPieceAreaViolationType.FREE_KICK_TOO_CLOSE,
            SetPieceAreaViolationType.FREE_KICK_OPPONENT_IN_ATTACK_PA,
            -> {
                repositionPlayerOutsideViolationArea(player, type)
                SetPieceRestartAwards.restartFreeKick(
                    server,
                    SetPieceRestartCause.fromViolation(player, type),
                )
            }
            SetPieceAreaViolationType.GOAL_KICK_BALL_IN_AREA,
            SetPieceAreaViolationType.FREE_KICK_BALL_IN_ATTACK_PA,
            -> {

            }
            else -> {

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
            SetPieceAreaViolationType.FREE_KICK_TOO_CLOSE -> {
                val spot = ctx?.ballPos ?: return
                outsideCirclePosition(player, spot, config.freeKickDistanceRadius, config)
            }
            SetPieceAreaViolationType.FREE_KICK_OPPONENT_IN_ATTACK_PA -> {
                val spotCtx = ctx ?: return
                val paSide = MatchFieldAreaUtil.penaltyAreaSideContainingBall(spotCtx.ballPos, config)
                if (paSide == spotCtx.restartTeam) {
                    outsidePenaltyAreaPosition(player, paSide, config)
                } else {
                    null
                }
            }
            SetPieceAreaViolationType.GOAL_KICK_BALL_IN_AREA,
            SetPieceAreaViolationType.FREE_KICK_BALL_IN_ATTACK_PA,
            -> null
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
            PenaltyShootoutState.isActive() -> PenaltyShootoutState.penaltyGoalTeam
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
            HashSet(),
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
