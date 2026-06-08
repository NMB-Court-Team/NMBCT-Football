package net.astrorbits.football.client.match

import net.astrorbits.football.match.PenaltyShootoutTiming
import net.astrorbits.football.match.TeamSide

object PenaltyKickClient {
    private val DURATION_MS = PenaltyShootoutTiming.KICK_BANNER_MS

    var startTimeMs: Long = 0L; private set
    var kickerTeam: TeamSide = TeamSide.A; private set
    var kickerName: String = ""; private set
    var penaltyScoreA: Int = 0; private set
    var penaltyScoreB: Int = 0; private set
    var kickNumber: Int = 0; private set
    var suddenDeath: Boolean = false; private set
    var teamAName: String = ""; private set
    var teamBName: String = ""; private set
    var scored: Boolean = false; private set

    val isActive: Boolean
        get() = startTimeMs > 0 && (System.currentTimeMillis() - startTimeMs) < DURATION_MS

    val elapsedMs: Long get() = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0L

    fun show(
        kickerTeam: TeamSide,
        kickerName: String,
        penaltyScoreA: Int,
        penaltyScoreB: Int,
        kickNumber: Int,
        suddenDeath: Boolean,
        teamAName: String,
        teamBName: String,
        scored: Boolean = false,
    ) {
        this.kickerTeam = kickerTeam
        this.kickerName = kickerName
        this.penaltyScoreA = penaltyScoreA
        this.penaltyScoreB = penaltyScoreB
        this.kickNumber = kickNumber
        this.suddenDeath = suddenDeath
        this.teamAName = teamAName
        this.teamBName = teamBName
        this.scored = scored
        startTimeMs = System.currentTimeMillis()
    }
}
