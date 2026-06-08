package net.astrorbits.football.client.match

import net.astrorbits.football.match.FreeKickType
import net.astrorbits.football.match.GoalLineOutType
import net.astrorbits.football.match.SetPieceKind
import net.astrorbits.football.match.SetPieceRestartKind
import net.astrorbits.football.match.TeamSide

object SetPiecePendingRestart {
    fun fromGoalLineOut(outType: GoalLineOutType, restartTeam: TeamSide) {
        val kind = when (outType) {
            GoalLineOutType.GOAL_KICK -> SetPieceKind.GOAL_KICK
            GoalLineOutType.CORNER_KICK -> SetPieceKind.CORNER_KICK
            GoalLineOutType.THROW_IN -> SetPieceKind.THROW_IN
        }
        MatchStartClient.beginBallResetPending(restartTeam, kind)
    }

    fun fromFreeKickAward(type: FreeKickType, restartTeam: TeamSide) {
        if (type == FreeKickType.PENALTY) return
        MatchStartClient.beginBallResetPending(restartTeam, SetPieceKind.FREE_KICK)
    }

    fun fromRestartAward(kind: SetPieceRestartKind, restartTeam: TeamSide) {
        val setPieceKind = when (kind) {
            SetPieceRestartKind.KICKOFF -> SetPieceKind.CENTER_KICKOFF
            SetPieceRestartKind.GOAL_KICK -> SetPieceKind.GOAL_KICK
            SetPieceRestartKind.CORNER_KICK -> SetPieceKind.CORNER_KICK
            SetPieceRestartKind.THROW_IN -> SetPieceKind.THROW_IN
            SetPieceRestartKind.FREE_KICK -> SetPieceKind.FREE_KICK
            SetPieceRestartKind.PENALTY_KICK -> SetPieceKind.PENALTY_KICK
        }
        MatchStartClient.beginBallResetPending(restartTeam, setPieceKind)
    }

    fun fromGoalScored(scoringTeam: TeamSide) {
        MatchStartClient.beginBallResetPending(scoringTeam.opponent(), SetPieceKind.CENTER_KICKOFF)
    }
}
