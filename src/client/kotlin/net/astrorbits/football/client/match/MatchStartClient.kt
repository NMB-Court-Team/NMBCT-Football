package net.astrorbits.football.client.match

import net.astrorbits.football.match.TeamSide

object MatchStartClient {
    private const val DURATION_MS = 6000L

    var startTimeMs: Long = 0L; private set
    var playerTeam: TeamSide = TeamSide.A; private set
    var isGk: Boolean = false; private set
    var kickoffTeam: TeamSide = TeamSide.A; private set
    var teamAName: String = ""; private set
    var teamBName: String = ""; private set

    val isActive: Boolean
        get() = startTimeMs > 0 && (System.currentTimeMillis() - startTimeMs) < DURATION_MS

    fun start(team: TeamSide, gk: Boolean, kickoff: TeamSide, nameA: String, nameB: String) {
        playerTeam = team
        isGk = gk
        kickoffTeam = kickoff
        teamAName = nameA
        teamBName = nameB
        startTimeMs = System.currentTimeMillis()
    }

    val elapsedMs: Long get() = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0L
    fun reset() { startTimeMs = 0L }
}
