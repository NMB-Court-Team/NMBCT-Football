package net.astrorbits.football.match

import net.astrorbits.football.network.FootballNetworking
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3

object SetPieceBootstrap {
    fun onAfterReset(level: ServerLevel, action: PendingAfterReset) {
        val server = level.server
        SetPieceAreaViolationMonitor.clearAll(server)
        SecondTouchTracker.clear()
        GoalKickSetPieceFlow.clear(server)
        ThrowInSetPieceFlow.clear(server)
        FreeKickSetPieceFlow.clear(server)
        SetPieceState.clear()

        when (action) {
            is PendingAfterReset.PostGoal -> {
                val pos = MatchConfigHolder.current.kickOff.let { Vec3(it.x, it.y, it.z) }
                SetPieceState.begin(
                    SetPieceContext(
                        kind = SetPieceKind.CENTER_KICKOFF,
                        restartTeam = action.kickoffTeam,
                        ballPos = pos,
                    ),
                )
                SetPieceState.active?.let { SetPiecePlayerRepositioner.repositionInitialViolators(server, it) }
            }
            is PendingAfterReset.GoalLineOut -> {
                val ballPos = action.ballPos ?: MatchConfigHolder.current.kickOff.let { Vec3(it.x, it.y, it.z) }
                when (action.outType) {
                    GoalLineOutType.GOAL_KICK -> {
                        val defending = action.defendingSide ?: action.kickoffTeam
                        GoalKickSetPieceFlow.begin(level, action.kickoffTeam, ballPos, defending)
                    }
                    GoalLineOutType.CORNER_KICK -> {
                        val defending = action.defendingSide ?: action.kickoffTeam.opponent()
                        CornerKickSetPieceFlow.begin(level, action.kickoffTeam, ballPos, defending)
                    }
                    GoalLineOutType.THROW_IN -> {
                        ThrowInSetPieceFlow.begin(
                            level,
                            action.kickoffTeam,
                            ballPos,
                            action.throwInTakerUuid,
                        )
                    }
                    null -> Unit
                }
            }
            is PendingAfterReset.MatchPenaltyKick -> Unit
            is PendingAfterReset.FreeKick -> {
                FreeKickSetPieceFlow.begin(
                    level,
                    action.kickoffTeam,
                    action.ballPos,
                    action.freeKickType,
                    action.preferredTakerUuid,
                    action.foulPos,
                )
            }
        }
        FootballNetworking.broadcastSetPieceState(server)
    }

    fun onCenterKickoffBegin(restartTeam: TeamSide, ballPos: Vec3, server: net.minecraft.server.MinecraftServer? = null) {
        SetPieceState.begin(
            SetPieceContext(
                kind = SetPieceKind.CENTER_KICKOFF,
                restartTeam = restartTeam,
                ballPos = ballPos,
            ),
        )
        if (server != null) {
            SetPieceState.active?.let { SetPiecePlayerRepositioner.repositionInitialViolators(server, it) }
        }
    }
}
