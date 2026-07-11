package net.astrorbits.football.client.match

import net.astrorbits.football.match.TeamSide

/** 全员左侧罚下广播 HUD 状态。 */
object SendOffBroadcastClient {
    private const val DURATION_MS = 6000L

    var startTimeMs: Long = 0L; private set
    var playerName: String = ""; private set
    var team: TeamSide = TeamSide.A; private set

    val isActive: Boolean
        get() = startTimeMs > 0 && (System.currentTimeMillis() - startTimeMs) < DURATION_MS

    val elapsedMs: Long get() = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0L

    fun show(name: String, sendOffTeam: TeamSide) {
        playerName = name
        team = sendOffTeam
        startTimeMs = System.currentTimeMillis()
    }

    fun clear() {
        startTimeMs = 0L
    }
}
