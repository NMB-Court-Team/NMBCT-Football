package net.astrorbits.football.match

import net.astrorbits.football.Football
import net.astrorbits.football.input.GoalkeeperHoldActionPermissions
import net.astrorbits.football.network.FootballNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.*

object FreeKickSetPieceFlow {
    fun begin(
        level: ServerLevel,
        awardedTeam: TeamSide,
        ballPos: Vec3,
        type: FreeKickType,
        preferredTakerUuid: UUID?,
        foulPos: Vec3,
    ) {
        val server = level.server
        val defendingSide = awardedTeam.opponent()
        val config = MatchConfigHolder.current
        var resolvedBallPos = ballPos
        if (type == FreeKickType.INDIRECT &&
            MatchFieldAreaUtil.isInGoalArea(config, defendingSide, ballPos.x, ballPos.z)
        ) {
            resolvedBallPos = MatchFieldAreaUtil.repositionIndirectFreeKickInGoalArea(ballPos, defendingSide, config)
            moveBallTo(level, resolvedBallPos)
        }
        val taker = pickTaker(server, awardedTeam, resolvedBallPos, preferredTakerUuid)
        SetPieceState.begin(
            SetPieceContext(
                kind = SetPieceKind.FREE_KICK,
                restartTeam = awardedTeam,
                ballPos = resolvedBallPos,
                defendingSide = defendingSide,
                freeKickType = type,
                freeKickTakerUuid = taker?.uuid,
                foulPos = foulPos,
            ),
        )
        if (type == FreeKickType.INDIRECT) {
            MatchState.beginThrowInDirectGoalRestriction()
        }
        taker?.let { player ->
            val (stand, yaw) = SetPieceTakerPlacement.freeKickTakerStand(resolvedBallPos, defendingSide)
            SetPieceTakerPlacement.teleportPlayer(level, player, stand, yaw)
        }
        SetPieceState.active?.let { SetPiecePlayerRepositioner.repositionInitialViolators(server, it) }
        resetHoldPermissions(server)
        FootballNetworking.broadcastSetPieceState(server)
    }

    fun onDefendingGoalkeeperDistributed(player: ServerPlayer) {
        val ctx = SetPieceState.active ?: return
        if (ctx.kind != SetPieceKind.FREE_KICK) return
        val team = MatchState.getPlayerTeam(player.uuid) ?: return
        if (team == ctx.restartTeam) return
        val server = player.level().server
        clear(server)
    }

    fun clear(server: MinecraftServer) {
        if (SetPieceState.active?.kind == SetPieceKind.FREE_KICK) {
            SetPieceState.clear()
            FootballNetworking.broadcastSetPieceState(server)
        }
    }

    private fun pickTaker(
        server: MinecraftServer,
        team: TeamSide,
        ballPos: Vec3,
        preferred: UUID?,
    ): ServerPlayer? {
        preferred?.let { uuid ->
            val player = server.playerList.getPlayer(uuid)
            if (player != null &&
                MatchParticipation.isParticipating(player) &&
                MatchState.getPlayerTeam(player.uuid) == team &&
                !PlayerRoleState.isGoalkeeper(player)
            ) {
                return player
            }
        }
        return ThrowInSetPieceFlow.pickThrowInTaker(server, team, ballPos)
    }

    private fun resetHoldPermissions(server: MinecraftServer) {
        for (player in server.playerList.players) {
            if (MatchParticipation.isParticipating(player)) {
                GoalkeeperHoldActionPermissions.resetToDefaults(player)
            }
        }
    }

    private fun moveBallTo(level: ServerLevel, pos: Vec3) {
        val all = AABB(Vec3(-3.0E7, -3.0E7, -3.0E7), Vec3(3.0E7, 3.0E7, 3.0E7))
        level.getEntitiesOfClass(Football::class.java, all).firstOrNull()
            ?.teleportBall(pos.x, pos.y, pos.z)
    }
}
