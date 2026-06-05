package net.astrorbits.football.client.match

import net.astrorbits.football.match.SetPieceRestartKind
import net.astrorbits.football.match.TeamSide

object SetPieceRestartClient {
    var isActive: Boolean = false; private set
    var kind: SetPieceRestartKind = SetPieceRestartKind.KICKOFF; private set
    var restartTeam: TeamSide = TeamSide.A; private set
    var startMs: Long = 0L; private set

    fun show(kind: SetPieceRestartKind, restartTeam: TeamSide) {
        this.kind = kind
        this.restartTeam = restartTeam
        this.startMs = System.currentTimeMillis()
        this.isActive = true
    }

    val elapsedMs: Long
        get() = if (isActive) System.currentTimeMillis() - startMs else 0L

    fun hide() {
        isActive = false
    }
}
