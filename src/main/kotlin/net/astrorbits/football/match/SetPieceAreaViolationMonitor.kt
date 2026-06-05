package net.astrorbits.football.match

import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.util.GoalkeeperUtil
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SetPieceAreaViolationMonitor {
    private const val VIOLATION_TICKS = 60

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
                val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
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
            -> SetPieceRestartAwards.restartCenterKickoff(server)
            SetPieceAreaViolationType.GOAL_KICK_OPPONENT_IN_AREA -> SetPieceRestartAwards.restartGoalKick(server)
            SetPieceAreaViolationType.CORNER_KICK_OPPONENT_IN_AREA -> SetPieceRestartAwards.restartCornerKick(server)
            SetPieceAreaViolationType.THROW_IN_OPPONENT_IN_AREA -> SetPieceRestartAwards.restartThrowIn(server)
            SetPieceAreaViolationType.PENALTY_KICK_INTRUSION -> Unit
        }
    }
}
