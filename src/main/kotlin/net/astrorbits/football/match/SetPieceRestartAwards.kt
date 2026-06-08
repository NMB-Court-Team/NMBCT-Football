package net.astrorbits.football.match

import net.astrorbits.football.FootballSounds
import net.astrorbits.football.network.FootballNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.world.phys.Vec3

object SetPieceRestartAwards {
    fun restartCenterKickoff(server: MinecraftServer, cause: SetPieceRestartCause = SetPieceRestartCause.NONE) {
        val kickoffTeam = MatchState.kickoffTeam ?: return
        val context = MatchState.kickoffWhistleContext() ?: KickoffWhistleContext.POST_GOAL
        val lockMs = when (context) {
            KickoffWhistleContext.MATCH_START -> MatchKickoffTiming.MATCH_START_LOCK_MS
            else -> MatchKickoffTiming.POST_GOAL_LOCK_MS
        }
        val level = server.overworld()
        val pos = MatchConfigHolder.current.kickOff.let { Vec3(it.x, it.y, it.z) }
        clearViolationsAndFlows(server)
        MatchState.kickoffTouched = false
        MatchState.resetFootball(level, pos)
        MatchState.kickoffTeam = kickoffTeam
        MatchState.beginKickoffPhase(lockMs, context)
        SetPieceBootstrap.onCenterKickoffBegin(kickoffTeam, pos, server)
        FootballNetworking.broadcastRestartKickoff(server, kickoffTeam, goalLineOut = false)
        FootballNetworking.broadcastSetPieceState(server)
        broadcastRestart(server, SetPieceRestartKind.KICKOFF, kickoffTeam, cause)
    }

    fun restartGoalKick(server: MinecraftServer, cause: SetPieceRestartCause = SetPieceRestartCause.NONE) {
        val ctx = SetPieceState.active ?: return
        if (ctx.kind != SetPieceKind.GOAL_KICK) return
        val level = server.overworld()
        clearViolationsAndFlows(server)
        MatchState.postGoalResetPending = true
        PostGoalBallResetScheduler.schedule(
            level,
            ctx.ballPos,
            PendingAfterReset.GoalLineOut(
                kickoffTeam = ctx.restartTeam,
                outType = GoalLineOutType.GOAL_KICK,
                ballPos = ctx.ballPos,
                defendingSide = ctx.defendingSide,
            ),
        )
        broadcastRestart(server, SetPieceRestartKind.GOAL_KICK, ctx.restartTeam, cause)
    }

    fun restartCornerKick(server: MinecraftServer, cause: SetPieceRestartCause = SetPieceRestartCause.NONE) {
        val ctx = SetPieceState.active ?: return
        if (ctx.kind != SetPieceKind.CORNER_KICK) return
        val level = server.overworld()
        clearViolationsAndFlows(server)
        MatchState.postGoalResetPending = true
        PostGoalBallResetScheduler.schedule(
            level,
            ctx.ballPos,
            PendingAfterReset.GoalLineOut(
                kickoffTeam = ctx.restartTeam,
                outType = GoalLineOutType.CORNER_KICK,
                ballPos = ctx.ballPos,
                defendingSide = ctx.defendingSide,
            ),
        )
        broadcastRestart(server, SetPieceRestartKind.CORNER_KICK, ctx.restartTeam, cause)
    }

    fun restartFreeKick(server: MinecraftServer, cause: SetPieceRestartCause = SetPieceRestartCause.NONE) {
        val ctx = SetPieceState.active ?: return
        if (ctx.kind != SetPieceKind.FREE_KICK) return
        val level = server.overworld()
        clearViolationsAndFlows(server)
        MatchState.kickoffTouched = false
        MatchState.postGoalResetPending = true
        PostGoalBallResetScheduler.schedule(
            level,
            ctx.ballPos,
            PendingAfterReset.FreeKick(
                kickoffTeam = ctx.restartTeam,
                ballPos = ctx.ballPos,
                freeKickType = ctx.freeKickType ?: FreeKickType.INDIRECT,
                preferredTakerUuid = ctx.freeKickTakerUuid,
                lastTouchPlayerUuid = null,
                foulPos = ctx.foulPos ?: ctx.ballPos,
            ),
        )
        broadcastRestart(server, SetPieceRestartKind.FREE_KICK, ctx.restartTeam, cause)
    }

    fun restartPenaltyKick(server: MinecraftServer, cause: SetPieceRestartCause = SetPieceRestartCause.NONE) {
        if (!MatchPenaltyKickState.isActive()) return
        val kickingTeam = MatchPenaltyKickState.kickingTeam
        val defendingTeam = MatchPenaltyKickState.defendingTeam
        val kicker = MatchPenaltyKickState.currentKickerUuid
        val level = server.overworld()
        val goal = MatchFieldAreaUtil.goalForSide(MatchConfigHolder.current, defendingTeam)
        val spot = goal.resolvedPenaltySpot()
        val ballPos = Vec3(spot.x, spot.y, spot.z)
        clearViolationsAndFlows(server)
        MatchPenaltyKickState.clear(server)
        MatchState.kickoffTouched = false
        MatchState.postGoalResetPending = true
        PostGoalBallResetScheduler.schedule(
            level,
            ballPos,
            PendingAfterReset.MatchPenaltyKick(
                kickoffTeam = kickingTeam,
                defendingTeam = defendingTeam,
                preferredKickerUuid = kicker,
                lastTouchPlayerUuid = null,
            ),
        )
        broadcastRestart(server, SetPieceRestartKind.PENALTY_KICK, kickingTeam, cause)
    }

    fun restartThrowIn(server: MinecraftServer, cause: SetPieceRestartCause = SetPieceRestartCause.NONE) {
        val ctx = SetPieceState.active ?: return
        if (ctx.kind != SetPieceKind.THROW_IN) return
        val level = server.overworld()
        clearViolationsAndFlows(server)
        MatchState.postGoalResetPending = true
        PostGoalBallResetScheduler.schedule(
            level,
            ctx.ballPos,
            PendingAfterReset.GoalLineOut(
                kickoffTeam = ctx.restartTeam,
                outType = GoalLineOutType.THROW_IN,
                ballPos = ctx.ballPos,
                throwInDirectGoalRestrict = true,
                throwInTakerUuid = ctx.throwInTakerUuid,
            ),
        )
        broadcastRestart(server, SetPieceRestartKind.THROW_IN, ctx.restartTeam, cause)
    }

    private fun clearViolationsAndFlows(server: MinecraftServer) {
        SetPieceAreaViolationMonitor.clearAll(server)
        SecondTouchTracker.clear()
        GoalKickSetPieceFlow.clear(server)
        ThrowInSetPieceFlow.clear(server)
        FreeKickSetPieceFlow.clear(server)
        SetPieceState.clear()
    }

    private fun broadcastRestart(
        server: MinecraftServer,
        kind: SetPieceRestartKind,
        team: TeamSide,
        cause: SetPieceRestartCause,
    ) {
        FootballSounds.playMatchWhistle(server, 6)
        FootballNetworking.broadcastSetPieceRestart(server, kind, team, cause)
    }
}
