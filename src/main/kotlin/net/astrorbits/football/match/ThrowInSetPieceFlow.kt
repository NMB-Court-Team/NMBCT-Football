package net.astrorbits.football.match

import net.astrorbits.football.Football
import net.astrorbits.football.input.GoalkeeperHoldActionPermissions
import net.astrorbits.football.network.FootballNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

object ThrowInSetPieceFlow {
    private val anchorPositions = mutableMapOf<java.util.UUID, Vec3>()

    fun begin(level: ServerLevel, restartTeam: TeamSide, ballPos: Vec3) {
        val server = level.server ?: return
        val taker = pickThrowInTaker(server, restartTeam, ballPos) ?: return
        val football = findFootballNear(level, ballPos) ?: return

        taker.teleportTo(level, ballPos.x, ballPos.y, ballPos.z, java.util.HashSet(), taker.yRot, taker.xRot, false)
        football.enterHold(taker)
        anchorPositions[taker.uuid] = Vec3(ballPos.x, ballPos.y, ballPos.z)

        GoalkeeperHoldActionPermissions.setAll(taker, catch = false, drop = false, throwBall = true)

        SetPieceState.begin(
            SetPieceContext(
                kind = SetPieceKind.THROW_IN,
                restartTeam = restartTeam,
                ballPos = ballPos,
                throwInTakerUuid = taker.uuid,
            ),
        )
        FootballNetworking.broadcastSetPieceState(server)
    }

    fun onBallThrown(player: ServerPlayer) {
        val ctx = SetPieceState.active ?: return
        if (ctx.kind != SetPieceKind.THROW_IN) return
        if (player.uuid != ctx.throwInTakerUuid) return
        clear(player.level().server)
        MatchState.notifyKickoffBallTouched(player)
    }

    fun tickMovementFreeze(server: MinecraftServer) {
        val ctx = SetPieceState.active ?: return
        if (ctx.kind != SetPieceKind.THROW_IN) return
        val takerUuid = ctx.throwInTakerUuid ?: return
        val taker = server.playerList.getPlayer(takerUuid) ?: return
        val anchor = anchorPositions[takerUuid] ?: ctx.ballPos
        if (taker.x != anchor.x || taker.y != anchor.y || taker.z != anchor.z) {
            taker.teleportTo(
                taker.level() as ServerLevel,
                anchor.x, anchor.y, anchor.z,
                java.util.HashSet(),
                taker.yRot, taker.xRot, false,
            )
        }
        taker.setDeltaMovement(Vec3.ZERO)
    }

    fun isMovementFrozen(player: ServerPlayer): Boolean {
        val ctx = SetPieceState.active ?: return false
        return ctx.kind == SetPieceKind.THROW_IN && player.uuid == ctx.throwInTakerUuid
    }

    fun clear(server: MinecraftServer?) {
        if (SetPieceState.active?.kind != SetPieceKind.THROW_IN) return
        val takerUuid = SetPieceState.active?.throwInTakerUuid
        if (takerUuid != null) {
            anchorPositions.remove(takerUuid)
            server?.playerList?.getPlayer(takerUuid)?.let {
                GoalkeeperHoldActionPermissions.resetToDefaults(it)
            }
        }
        SetPieceState.clear()
        server?.let { FootballNetworking.broadcastSetPieceState(it) }
    }

    private fun pickThrowInTaker(server: MinecraftServer, restartTeam: TeamSide, ballPos: Vec3): ServerPlayer? {
        val gkUuid = when (restartTeam) {
            TeamSide.A -> PlayerRoleState.teamAGoalkeeper
            TeamSide.B -> PlayerRoleState.teamBGoalkeeper
        }
        return server.playerList.players
            .filter { MatchParticipation.isParticipating(it) }
            .filter { MatchState.getPlayerTeam(it.uuid) == restartTeam }
            .filter { it.uuid != gkUuid && !PlayerRoleState.isGoalkeeper(it) }
            .minByOrNull { horizontalDistance(it, ballPos) }
    }

    private fun horizontalDistance(player: ServerPlayer, pos: Vec3): Double {
        val dx = player.x - pos.x
        val dz = player.z - pos.z
        return sqrt(dx * dx + dz * dz)
    }

    private fun findFootballNear(level: ServerLevel, pos: Vec3): Football? {
        val box = AABB.ofSize(pos, 4.0, 4.0, 4.0)
        return level.getEntitiesOfClass(Football::class.java, box).minByOrNull { it.distanceToSqr(pos) }
    }
}
