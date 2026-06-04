package net.astrorbits.football.client.match

import net.astrorbits.football.match.PenaltyKickPhase
import net.astrorbits.football.match.TeamSide

object PenaltyShootoutClient {
    var active: Boolean = false
    var penaltyScoreA: Int = 0
    var penaltyScoreB: Int = 0
    var suddenDeath: Boolean = false
    var totalKicksTaken: Int = 0
    var currentKickerTeam: TeamSide = TeamSide.A
    var kickerName: String = ""
    var kickPhase: PenaltyKickPhase = PenaltyKickPhase.SETUP
    var activeDefendingTeam: TeamSide = TeamSide.A
    var firstKickTeam: TeamSide = TeamSide.A

    fun sync(
        active: Boolean,
        penaltyScoreA: Int,
        penaltyScoreB: Int,
        suddenDeath: Boolean,
        totalKicksTaken: Int,
        currentKickerTeam: TeamSide,
        kickerName: String,
        kickPhase: PenaltyKickPhase,
        activeDefendingTeam: TeamSide,
        firstKickTeam: TeamSide,
    ) {
        this.active = active
        this.penaltyScoreA = penaltyScoreA
        this.penaltyScoreB = penaltyScoreB
        this.suddenDeath = suddenDeath
        this.totalKicksTaken = totalKicksTaken
        this.currentKickerTeam = currentKickerTeam
        this.kickerName = kickerName
        this.kickPhase = kickPhase
        this.activeDefendingTeam = activeDefendingTeam
        this.firstKickTeam = firstKickTeam
    }

    fun reset() {
        active = false
        penaltyScoreA = 0
        penaltyScoreB = 0
        suddenDeath = false
        totalKicksTaken = 0
        kickerName = ""
        kickPhase = PenaltyKickPhase.SETUP
    }
}
