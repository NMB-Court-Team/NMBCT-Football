package net.astrorbits.football.client.match

import net.astrorbits.football.match.TeamSide

object InvalidGoalClient {
    private const val DURATION_MS = 4000L

    var startTimeMs: Long = 0L; private set
    var scorerName: String = ""; private set
    var scorerTeam: TeamSide = TeamSide.A; private set
    var teamAScore: Int = 0; private set
    var teamBScore: Int = 0; private set
    var teamAName: String = ""; private set
    var teamBName: String = ""; private set

    val isActive: Boolean
        get() = startTimeMs > 0 && (System.currentTimeMillis() - startTimeMs) < DURATION_MS

    val elapsedMs: Long get() = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0L

    fun show(
        scorer: String,
        scorerT: TeamSide,
        scoreA: Int,
        scoreB: Int,
        nameA: String,
        nameB: String,
    ) {
        scorerName = scorer
        scorerTeam = scorerT
        teamAScore = scoreA
        teamBScore = scoreB
        teamAName = nameA
        teamBName = nameB
        startTimeMs = System.currentTimeMillis()
    }

    fun name(side: TeamSide): String = when (side) {
        TeamSide.A -> teamAName
        TeamSide.B -> teamBName
    }
}
