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
        SetPieceTakerPlacement.resolveGoalkeeper(server, restartTeam)?.let { keeper ->
            val (stand, yaw) = SetPieceTakerPlacement.goalKickKeeperStand(restartTeam, ballPos)
            SetPieceTakerPlacement.teleportPlayer(level, keeper, stand, yaw)
        }
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

    /**
     * [GoalKickPhase.PLACED] 期间非主罚员触球 → 重新发球门球。
     * @return true 表示已触发重发，调用方应中止后续开球/持球逻辑。
     */
    fun tryRestartOnInvalidPlacedTouch(player: ServerPlayer, server: MinecraftServer): Boolean {
        val ctx = SetPieceState.active ?: return false
        if (ctx.kind != SetPieceKind.GOAL_KICK || ctx.goalKickPhase != GoalKickPhase.PLACED) {
            return false
        }
        if (isPlacedKicker(player)) {
            return false
        }
        findMatchFootball(server.overworld())?.releaseHold()
        SetPieceRestartAwards.restartGoalKick(server)
        return true
    }

    /**
     * [GoalKickPhase.AWAITING_PA_EXIT] 期间球仍在大禁区内被其他球员触球 → 重新发球门球。
     */
    fun tryRestartOnAwaitingExitTouch(player: ServerPlayer, server: MinecraftServer): Boolean {
        val ctx = SetPieceState.active ?: return false
        if (ctx.kind != SetPieceKind.GOAL_KICK || ctx.goalKickPhase != GoalKickPhase.AWAITING_PA_EXIT) {
            return false
        }
        val ball = findMatchFootball(server.overworld()) ?: return false
        val penaltyAreaSide = penaltyAreaSide(ctx)
        val ballPos = Vec3(ball.x, ball.y, ball.z)
        if (!MatchFieldAreaUtil.isBallInPenaltyArea(penaltyAreaSide, ballPos)) {
            completeAwaitingPaExit(server, ball)
            return false
        }
        ball.releaseHold()
        SetPieceRestartAwards.restartGoalKick(server)
        return true
    }

    fun tryRestartOnGoalKickTouch(player: ServerPlayer, server: MinecraftServer): Boolean {
        if (tryRestartOnInvalidPlacedTouch(player, server)) return true
        return tryRestartOnAwaitingExitTouch(player, server)
    }

    /** 主罚员正式踢出后进入等待出区阶段；此阶段不解除开球锁。 */
    fun onBallMoved(server: MinecraftServer, football: Football, actingPlayer: ServerPlayer? = null) {
        val ctx = SetPieceState.active ?: return
        if (ctx.kind != SetPieceKind.GOAL_KICK) return
        if (ctx.goalKickPhase != GoalKickPhase.PLACED) return
        if (actingPlayer != null && tryRestartOnInvalidPlacedTouch(actingPlayer, server)) {
            return
        }
        SetPieceState.update { it.copy(goalKickPhase = GoalKickPhase.AWAITING_PA_EXIT) }
        applyTeamPermissions(
            server,
            ctx.restartTeam,
            ctx.defendingSide ?: ctx.restartTeam.opponent(),
            pickerUuid = ctx.goalKickPickerUuid,
        )
        FootballNetworking.broadcastSetPieceState(server)
    }

    fun isAwaitingPenaltyAreaExit(): Boolean {
        val ctx = SetPieceState.active ?: return false
        return ctx.kind == SetPieceKind.GOAL_KICK && ctx.goalKickPhase == GoalKickPhase.AWAITING_PA_EXIT
    }

    fun penaltyAreaSide(ctx: SetPieceContext): TeamSide =
        ctx.defendingSide ?: ctx.restartTeam

    fun completeAwaitingPaExit(server: MinecraftServer, football: Football) {
        val ctx = SetPieceState.active ?: return
        if (ctx.kind != SetPieceKind.GOAL_KICK || ctx.goalKickPhase != GoalKickPhase.AWAITING_PA_EXIT) {
            return
        }
        val kickerUuid = ctx.goalKickPickerUuid
        val restartTeam = ctx.restartTeam
        val level = server.overworld()
        val resumeTick = level.gameTime
        if (kickerUuid != null) {
            SecondTouchTracker.beginAfterOpeningPlay(
                restartTeam = restartTeam,
                takerUuid = kickerUuid,
                sourceKind = SetPieceKind.GOAL_KICK,
                resumeGameTick = resumeTick,
            )
        }
        clear(server)
        val kicker = kickerUuid?.let { level.getEntity(it) as? ServerPlayer }
        if (kicker != null) {
            MatchState.notifyKickoffBallTouched(kicker)
        } else {
            MatchState.forceKickoffBallTouched()
        }
    }

    fun canDropInGoalArea(player: ServerPlayer): Boolean {
        val ctx = SetPieceState.active ?: return true
        if (ctx.kind != SetPieceKind.GOAL_KICK) return true
        if (ctx.goalKickPhase != GoalKickPhase.PLACING) return true
        if (player.uuid != ctx.goalKickPickerUuid) return true
        val goalAreaSide = ctx.defendingSide ?: ctx.restartTeam
        return MatchFieldAreaUtil.isPlayerInGoalArea(player, goalAreaSide)
    }

    fun clear(server: MinecraftServer?) {
        if (SetPieceState.active?.kind != SetPieceKind.GOAL_KICK) {
            return
        }
        restoreAllPermissions(server)
        SetPieceState.clear()
        server?.let { FootballNetworking.broadcastSetPieceState(it) }
    }

    fun findMatchFootball(level: ServerLevel): Football? {
        val all = AABB(Vec3(-3.0E7, -3.0E7, -3.0E7), Vec3(3.0E7, 3.0E7, 3.0E7))
        return level.getEntitiesOfClass(Football::class.java, all).firstOrNull()
    }

    /** [GoalKickPhase.PLACED] 时发球方距球水平距离最近的参赛球员。 */
    fun findClosestPlacedKicker(server: MinecraftServer, restartTeam: TeamSide, ballX: Double, ballZ: Double): UUID? =
        GoalKickPlacedKickerUtil.findClosestParticipatingPlayer(
            players = server.playerList.players,
            restartTeam = restartTeam,
            ballX = ballX,
            ballZ = ballZ,
            teamOf = { player -> MatchState.getPlayerTeam(player.uuid) },
            isParticipating = MatchParticipation::isParticipating,
        )

    fun isPlacedKicker(player: ServerPlayer): Boolean {
        val ctx = SetPieceState.active ?: return false
        if (ctx.kind != SetPieceKind.GOAL_KICK || ctx.goalKickPhase != GoalKickPhase.PLACED) return false
        val team = MatchState.getPlayerTeam(player.uuid) ?: return false
        if (team != ctx.restartTeam) return false
        ctx.goalKickPickerUuid?.let { return player.uuid == it }
        val football = findMatchFootball(player.level().server.overworld()) ?: return false
        val closest = findClosestPlacedKicker(
            player.level().server,
            ctx.restartTeam,
            football.x,
            football.z,
        ) ?: return false
        return player.uuid == closest
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
                player.uuid == pickerUuid &&
                    SetPieceState.active?.goalKickPhase == GoalKickPhase.PLACING -> Unit
                team == restartTeam -> {
                    when (SetPieceState.active?.goalKickPhase) {
                        GoalKickPhase.WAITING_PICKUP ->
                            GoalkeeperHoldActionPermissions.setAll(player, catch = true, drop = false, throwBall = false)
                        GoalKickPhase.PLACED, GoalKickPhase.AWAITING_PA_EXIT ->
                            GoalkeeperHoldActionPermissions.resetToDefaults(player)
                        else ->
                            GoalkeeperHoldActionPermissions.setAll(player, catch = false, drop = false, throwBall = false)
                    }
                }
                team == defendingSide.opponent() -> {
                    when (SetPieceState.active?.goalKickPhase) {
                        GoalKickPhase.AWAITING_PA_EXIT ->
                            GoalkeeperHoldActionPermissions.resetToDefaults(player)
                        else ->
                            GoalkeeperHoldActionPermissions.setAll(player, catch = false, drop = false, throwBall = false)
                    }
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
}
