package net.astrorbits.football.client.match

import net.astrorbits.football.match.GoalLineOutType
import net.astrorbits.football.match.TeamSide

object GoalLineOutClient {
    private const val DURATION_MS = 4000L

    var startTimeMs: Long = 0L; private set
    var outType: GoalLineOutType = GoalLineOutType.GOAL_KICK; private set
    var restartTeam: TeamSide = TeamSide.A; private set
    var lastTouchPlayerName: String = ""; private set
    var lastTouchTeam: TeamSide? = null; private set

    val isActive: Boolean
        get() = startTimeMs > 0 && (System.currentTimeMillis() - startTimeMs) < DURATION_MS

    val elapsedMs: Long get() = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0L

    fun show(
        type: GoalLineOutType,
        team: TeamSide,
        touchName: String,
        touchTeam: TeamSide?,
    ) {
        outType = type
        restartTeam = team
        lastTouchPlayerName = touchName
        lastTouchTeam = touchTeam
        startTimeMs = System.currentTimeMillis()
    }
}
