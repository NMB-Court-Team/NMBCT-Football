package net.astrorbits.football.match

import net.astrorbits.football.network.FootballNetworking
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import java.util.*

/** 正赛点球判罚（禁区内滑铲等犯规）。 */
object PenaltyKickAwards {
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

        MatchState.clearPendingGoalLineOut()
        MatchState.clearDirectGoalRestriction()
        MatchState.clearPendingOffsideSnapshot()
        SetPieceState.clear()
        GoalKickSetPieceFlow.clear(level.server)
        ThrowInSetPieceFlow.clear(level.server)
        level.server?.let { SetPieceAreaViolationMonitor.clearAll(it) }
        MatchState.postGoalResetPending = true

        PostGoalBallResetScheduler.schedule(
            level,
            resetPos = ballPos,
            afterReset = PendingAfterReset.MatchPenaltyKick(
                kickoffTeam = kickingTeam,
                defendingTeam = defendingTeam,
                preferredKickerUuid = fouledPlayerUuid,
                lastTouchPlayerUuid = foulingPlayerUuid,
            ),
        )

        val server = level.server ?: return false
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
        return true
    }

    private fun canAward(level: ServerLevel): Boolean {
        if (!MatchState.isDuringMatch()) return false
        if (MatchState.currentPhase == MatchPhase.PENALTIES) return false
        if (MatchState.postGoalResetPending) return false
        if (MatchPenaltyKickState.isActive()) return false
        return level.server != null
    }
}
