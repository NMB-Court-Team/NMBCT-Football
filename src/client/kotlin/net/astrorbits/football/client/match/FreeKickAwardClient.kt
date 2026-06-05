package net.astrorbits.football.client.match

import net.astrorbits.football.match.FreeKickFoulReason
import net.astrorbits.football.match.FreeKickType
import net.astrorbits.football.match.TeamSide

object FreeKickAwardClient {
    private const val DURATION_MS = 4000L

    var startTimeMs: Long = 0L; private set
    var freeKickType: FreeKickType = FreeKickType.INDIRECT; private set
    var foulReason: FreeKickFoulReason = FreeKickFoulReason.OFFSIDE; private set
    var foulingPlayerName: String = ""; private set
    var foulingTeam: TeamSide = TeamSide.A; private set
    var restartTeam: TeamSide = TeamSide.A; private set

    val isActive: Boolean
        get() = startTimeMs > 0 && (System.currentTimeMillis() - startTimeMs) < DURATION_MS

    val elapsedMs: Long get() = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0L

    fun show(
        type: FreeKickType,
        reason: FreeKickFoulReason,
        playerName: String,
        foulingT: TeamSide,
        restartT: TeamSide,
    ) {
        freeKickType = type
        foulReason = reason
        foulingPlayerName = playerName
        foulingTeam = foulingT
        restartTeam = restartT
        startTimeMs = System.currentTimeMillis()
    }
}
