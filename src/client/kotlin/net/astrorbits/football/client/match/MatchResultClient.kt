package net.astrorbits.football.client.match

object MatchResultClient {
    private const val DURATION_MS = 10000L

    var startTimeMs: Long = 0L; private set
    var teamAScore: Int = 0; private set
    var teamBScore: Int = 0; private set
    var teamAName: String = ""; private set
    var teamBName: String = ""; private set
    var isDraw: Boolean = false; private set

    val isActive: Boolean
        get() = startTimeMs > 0 && (System.currentTimeMillis() - startTimeMs) < DURATION_MS

    val elapsedMs: Long get() = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0L

    fun show(scoreA: Int, scoreB: Int, nameA: String, nameB: String, draw: Boolean) {
        teamAScore = scoreA; teamBScore = scoreB
        teamAName = nameA; teamBName = nameB
        isDraw = draw
        startTimeMs = System.currentTimeMillis()
    }
}
