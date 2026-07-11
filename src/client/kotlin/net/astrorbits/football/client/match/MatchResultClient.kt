package net.astrorbits.football.client.match

object MatchResultClient {
    private const val DURATION_MS = 10000L

    var startTimeMs: Long = 0L; private set
    var teamAScore: Int = 0; private set
    var teamBScore: Int = 0; private set
    var teamAName: String = ""; private set
    var teamBName: String = ""; private set
    var isDraw: Boolean = false; private set
    var wonByPenalties: Boolean = false; private set
    var penaltyScoreA: Int = 0; private set
    var penaltyScoreB: Int = 0; private set
    var penaltyWinner: net.astrorbits.football.match.TeamSide? = null; private set
    var forfeitWinner: net.astrorbits.football.match.TeamSide? = null; private set

    val isActive: Boolean
        get() = startTimeMs > 0 && (System.currentTimeMillis() - startTimeMs) < DURATION_MS

    val elapsedMs: Long get() = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0L

    fun show(
        scoreA: Int,
        scoreB: Int,
        nameA: String,
        nameB: String,
        draw: Boolean,
        wonByPenalties: Boolean = false,
        penaltyScoreA: Int = 0,
        penaltyScoreB: Int = 0,
        penaltyWinner: net.astrorbits.football.match.TeamSide? = null,
        forfeitWinner: net.astrorbits.football.match.TeamSide? = null,
    ) {
        teamAScore = scoreA
        teamBScore = scoreB
        teamAName = nameA
        teamBName = nameB
        isDraw = draw
        this.wonByPenalties = wonByPenalties
        this.penaltyScoreA = penaltyScoreA
        this.penaltyScoreB = penaltyScoreB
        this.penaltyWinner = penaltyWinner
        this.forfeitWinner = forfeitWinner
        startTimeMs = System.currentTimeMillis()
    }
}
