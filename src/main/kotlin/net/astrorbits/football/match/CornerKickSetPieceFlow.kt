package net.astrorbits.football.match

import net.astrorbits.football.network.FootballNetworking
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3

object CornerKickSetPieceFlow {
    fun begin(
        level: ServerLevel,
        restartTeam: TeamSide,
        ballPos: Vec3,
        defendingSide: TeamSide,
    ) {
        val server = level.server
        val taker = ThrowInSetPieceFlow.pickThrowInTaker(server, restartTeam, ballPos)
        SetPieceState.begin(
            SetPieceContext(
                kind = SetPieceKind.CORNER_KICK,
                restartTeam = restartTeam,
                ballPos = ballPos,
                defendingSide = defendingSide,
                cornerPos = ballPos,
                cornerKickTakerUuid = taker?.uuid,
            ),
        )
        taker?.let { player ->
            val (stand, yaw) = SetPieceTakerPlacement.cornerTakerStand(ballPos)
            SetPieceTakerPlacement.teleportPlayer(level, player, stand, yaw)
        }
        SetPieceState.active?.let { SetPiecePlayerRepositioner.repositionInitialViolators(server, it) }
        FootballNetworking.broadcastSetPieceState(server)
    }
}
