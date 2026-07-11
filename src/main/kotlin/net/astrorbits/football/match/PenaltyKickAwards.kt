package net.astrorbits.football.match

import net.astrorbits.football.Football
import net.astrorbits.football.network.FootballNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.*

/** 正赛点球判罚（禁区内滑铲等犯规）。 */
object PenaltyKickAwards {
    private val ALL_FOOTBALLS_AABB = AABB(Vec3(-3.0E7, -3.0E7, -3.0E7), Vec3(3.0E7, 3.0E7, 3.0E7))

    fun awardSlideTackleInPenaltyArea(
        level: ServerLevel,
        foulingTeam: TeamSide,
        foulingPlayerUuid: UUID,
        fouledPlayerUuid: UUID,
    ): Boolean {
        if (!canAward(level)) return false
        if (MatchState.getPlayerTeam(foulingPlayerUuid) != foulingTeam) return false
        if (MatchState.getPlayerTeam(fouledPlayerUuid) == foulingTeam) return false

        val kickingTeam = foulingTeam.opponent()
        val defendingTeam = foulingTeam
        val goal = MatchFieldAreaUtil.goalForSide(MatchConfigHolder.current, defendingTeam)
        val spot = goal.resolvedPenaltySpot()
        val ballPos = Vec3(spot.x, spot.y, spot.z)
        val pendingPenalty = PendingAfterReset.MatchPenaltyKick(
            kickoffTeam = kickingTeam,
            defendingTeam = defendingTeam,
            preferredKickerUuid = fouledPlayerUuid,
            lastTouchPlayerUuid = foulingPlayerUuid,
        )

        MatchState.clearPendingGoalLineOut()
        MatchState.clearDirectGoalRestriction()
        MatchState.clearPendingOffsideSnapshot()
        SetPieceState.clear()
        GoalKickSetPieceFlow.clear(level.server)
        ThrowInSetPieceFlow.clear(level.server)
        SetPieceAreaViolationMonitor.clearAll(level.server)

        val server = level.server
        val foulingPlayer = server.playerList.getPlayer(foulingPlayerUuid)
        if (foulingPlayer != null) {
            MatchSendOffState.sendOffForSlideTackleFoul(server, foulingPlayer, foulingTeam)
        }
        if (!MatchState.isDuringMatch()) {
            return true
        }
        FootballNetworking.broadcastFreeKickAward(
            server,
            type = FreeKickType.PENALTY,
            foulReason = FreeKickFoulReason.SLIDE_TACKLE_IN_PENALTY_AREA,
            foulingTeam = foulingTeam,
            restartTeam = kickingTeam,
            foulingPlayerUuid = foulingPlayerUuid,
            ballX = ballPos.x,
            ballY = ballPos.y,
            ballZ = ballPos.z,
        )

        val football = level.getEntitiesOfClass(Football::class.java, ALL_FOOTBALLS_AABB).firstOrNull()
        PenaltyFoulGoalWatchState.begin(server, level, pendingPenalty, football)
        return true
    }

    private fun canAward(level: ServerLevel): Boolean {
        if (!MatchState.isDuringMatch()) return false
        if (MatchState.currentPhase == MatchPhase.PENALTIES) return false
        if (MatchState.postGoalResetPending) return false
        if (PenaltyFoulGoalWatchState.isActive()) return false
        if (MatchPenaltyKickState.isActive()) return false
        return true
    }
}
