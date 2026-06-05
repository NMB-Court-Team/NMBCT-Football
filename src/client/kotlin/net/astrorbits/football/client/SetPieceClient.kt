package net.astrorbits.football.client

import net.astrorbits.football.match.GoalKickPhase
import net.astrorbits.football.match.SetPieceKind
import net.astrorbits.football.match.TeamSide
import java.util.UUID

object SetPieceClient {
    var kind: SetPieceKind = SetPieceKind.NONE; private set
    var restartTeam: TeamSide? = null; private set
    var goalKickPhase: GoalKickPhase? = null; private set
    var goalKickPickerUuid: UUID? = null; private set
    var throwInTakerUuid: UUID? = null; private set
    var movementFrozen: Boolean = false; private set

    fun sync(
        kind: SetPieceKind,
        restartTeam: TeamSide?,
        goalKickPhase: GoalKickPhase?,
        goalKickPickerUuid: UUID?,
        throwInTakerUuid: UUID?,
        movementFrozen: Boolean,
    ) {
        this.kind = kind
        this.restartTeam = restartTeam
        this.goalKickPhase = goalKickPhase
        this.goalKickPickerUuid = goalKickPickerUuid
        this.throwInTakerUuid = throwInTakerUuid
        this.movementFrozen = movementFrozen
    }

    fun reset() {
        sync(SetPieceKind.NONE, null, null, null, null, false)
    }

    fun isMovementFrozen(playerUuid: UUID): Boolean =
        movementFrozen && throwInTakerUuid == playerUuid
}
