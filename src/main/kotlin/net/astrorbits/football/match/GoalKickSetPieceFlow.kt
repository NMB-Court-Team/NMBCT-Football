package net.astrorbits.football.match

import net.astrorbits.football.Football
import net.astrorbits.football.input.GoalkeeperHoldActionPermissions
import net.astrorbits.football.input.GoalkeeperHoldLock
import net.astrorbits.football.network.FootballNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.*

object GoalKickSetPieceFlow {
    private const val GOAL_KICK_HOLD_LOCK_TICKS = 100

    fun begin(level: ServerLevel, restartTeam: TeamSide, ballPos: Vec3, defendingSide: TeamSide) {
        val server = level.server
        SetPieceState.begin(
            SetPieceContext(
                kind = SetPieceKind.GOAL_KICK,
                restartTeam = restartTeam,
                ballPos = ballPos,
                defendingSide = defendingSide,
                goalKickPhase = GoalKickPhase.WAITING_PICKUP,
            ),
        )
        SetPieceState.active?.let { SetPiecePlayerRepositioner.repositionInitialViolators(server, it) }
        applyTeamPermissions(server, restartTeam, defendingSide, pickerUuid = null)
        FootballNetworking.broadcastSetPieceState(server)
    }

    fun onPlayerCaughtBall(player: ServerPlayer, football: Football) {
        val ctx = SetPieceState.active ?: return
        if (ctx.kind != SetPieceKind.GOAL_KICK) return
        val team = MatchState.getPlayerTeam(player.uuid) ?: return
        if (team != ctx.restartTeam) return
        if (ctx.goalKickPhase != GoalKickPhase.WAITING_PICKUP) return

        SetPieceState.update {
            it.copy(
                goalKickPhase = GoalKickPhase.PLACING,
                goalKickPickerUuid = player.uuid,
            )
        }
        val server = player.level().server
        GoalkeeperHoldActionPermissions.setAll(player, catch = false, drop = true, throwBall = false)
        GoalkeeperHoldLock.beginLock(player, player.level().gameTime, GOAL_KICK_HOLD_LOCK_TICKS)
        applyTeamPermissions(server, ctx.restartTeam, ctx.defendingSide ?: team.opponent(), player.uuid)
        FootballNetworking.broadcastSetPieceState(server)
    }

    fun onBallDropped(player: ServerPlayer) {
        val ctx = SetPieceState.active ?: return
        if (ctx.kind != SetPieceKind.GOAL_KICK) return
        if (ctx.goalKickPhase != GoalKickPhase.PLACING) return
        if (player.uuid != ctx.goalKickPickerUuid) return

        val pickerUuid = ctx.goalKickPickerUuid
        SetPieceState.update { it.copy(goalKickPhase = GoalKickPhase.PLACED) }
        val server = player.level().server
        GoalkeeperHoldActionPermissions.resetToDefaults(player)
        applyTeamPermissions(server, ctx.restartTeam, ctx.defendingSide ?: ctx.restartTeam.opponent(), pickerUuid)
        FootballNetworking.broadcastSetPieceState(server)
    }

    fun onBallMoved(server: MinecraftServer, football: Football) {
        val ctx = SetPieceState.active ?: return
        if (ctx.kind != SetPieceKind.GOAL_KICK) return
        clear(server)
        val holderId = football.getHolderEntityId()
        if (holderId >= 0) {
            val player = server.overworld().getEntity(holderId) as? ServerPlayer
            if (player != null) {
                MatchState.notifyKickoffBallTouched(player)
            } else {
                MatchState.forceKickoffBallTouched()
            }
        } else {
            MatchState.forceKickoffBallTouched()
        }
    }

    fun canDropInGoalArea(player: ServerPlayer): Boolean {
        val ctx = SetPieceState.active ?: return true
        if (ctx.kind != SetPieceKind.GOAL_KICK) return true
        if (ctx.goalKickPhase != GoalKickPhase.PLACING) return true
        if (player.uuid != ctx.goalKickPickerUuid) return true
        val defending = ctx.defendingSide ?: return false
        return MatchFieldAreaUtil.isPlayerInGoalArea(player, defending)
    }

    fun clear(server: MinecraftServer?) {
        if (SetPieceState.active?.kind != SetPieceKind.GOAL_KICK) {
            return
        }
        restoreAllPermissions(server)
        SetPieceState.clear()
        server?.let { FootballNetworking.broadcastSetPieceState(it) }
    }

    private fun applyTeamPermissions(
        server: MinecraftServer,
        restartTeam: TeamSide,
        defendingSide: TeamSide,
        pickerUuid: UUID?,
    ) {
        for (player in server.playerList.players) {
            if (!MatchParticipation.isParticipating(player)) continue
            val team = MatchState.getPlayerTeam(player.uuid) ?: continue
            when {
                player.uuid == pickerUuid -> Unit
                team == restartTeam -> {
                    val canCatch = SetPieceState.active?.goalKickPhase == GoalKickPhase.WAITING_PICKUP
                    GoalkeeperHoldActionPermissions.setAll(player, catch = canCatch, drop = false, throwBall = false)
                }
                team == defendingSide.opponent() -> {
                    GoalkeeperHoldActionPermissions.setAll(player, catch = false, drop = false, throwBall = false)
                }
            }
        }
    }

    private fun restoreAllPermissions(server: MinecraftServer?) {
        server ?: return
        for (player in server.playerList.players) {
            GoalkeeperHoldActionPermissions.resetToDefaults(player)
        }
    }

    fun findActiveFootball(level: ServerLevel): Football? {
        val ctx = SetPieceState.active ?: return null
        if (ctx.kind != SetPieceKind.GOAL_KICK) return null
        val box = AABB.ofSize(ctx.ballPos, 8.0, 8.0, 8.0)
        return level.getEntitiesOfClass(Football::class.java, box)
            .minByOrNull { it.distanceToSqr(ctx.ballPos) }
    }
}
