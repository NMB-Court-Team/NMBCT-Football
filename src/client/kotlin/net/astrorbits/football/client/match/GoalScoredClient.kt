package net.astrorbits.football.client.match

import net.astrorbits.football.match.TeamSide

object GoalScoredClient {
    private const val DURATION_MS = 4000L

    var startTimeMs: Long = 0L; private set
    var scoringTeam: TeamSide = TeamSide.A; private set
    var scorerName: String = ""; private set
    var scorerTeam: TeamSide = TeamSide.A; private set
    var teamAScore: Int = 0; private set
    var teamBScore: Int = 0; private set
    var ownGoal: Boolean = false; private set
    var teamAName: String = ""; private set
    var teamBName: String = ""; private set

    val isActive: Boolean
        get() = startTimeMs > 0 && (System.currentTimeMillis() - startTimeMs) < DURATION_MS

    val elapsedMs: Long get() = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0L

    fun show(
        scoring: TeamSide,
        scorer: String,
        scorerT: TeamSide,
        scoreA: Int,
        scoreB: Int,
        og: Boolean,
        nameA: String,
        nameB: String,
    ) {
        scoringTeam = scoring
        scorerName = scorer
        scorerTeam = scorerT
        teamAScore = scoreA
        teamBScore = scoreB
        ownGoal = og
        teamAName = nameA
        teamBName = nameB
        startTimeMs = System.currentTimeMillis()
    }

    fun name(side: TeamSide): String = when (side) {
        TeamSide.A -> teamAName
        TeamSide.B -> teamBName
    }
}
