package net.astrorbits.football.client.match

import net.astrorbits.football.client.GoalkeeperStateClient
import net.astrorbits.football.client.SetPieceAreaViolationClient
import net.astrorbits.football.client.SetPieceClient
import net.astrorbits.football.match.MatchPhase
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.PenaltyKickPhase
import net.astrorbits.football.match.SetPieceKind
import net.astrorbits.football.network.*
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object MatchStartClientNetworking {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(MatchStartS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                MatchState.teamAName = Component.literal(payload.teamAName)
                MatchState.teamBName = Component.literal(payload.teamBName)
                if (MatchState.currentPhase == MatchPhase.PENALTIES) {
                    MatchStartClient.assignTeam(payload.playerTeam, payload.teamAName, payload.teamBName)
                } else {
                    MatchState.teamAScore = 0
                    MatchState.teamBScore = 0
                    MatchStartClient.startMatch(
                        payload.playerTeam, payload.isGk, payload.kickoffTeam,
                        payload.teamAName, payload.teamBName,
                    )
                }
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(PostGoalKickoffS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                if (payload.goalLineOut) {
                    MatchStartClient.startGoalLineOutKickoff(payload.kickoffTeam, payload.isKickoffTeam)
                } else {
                    MatchStartClient.startPostGoalKickoff(payload.kickoffTeam, payload.isKickoffTeam)
                }
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(KickoffBallTouchedS2CPayload.TYPE) { _, _ ->
            Minecraft.getInstance().execute {
                MatchStartClient.onBallTouched()
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(MatchResetS2CPayload.TYPE) { _, _ ->
            Minecraft.getInstance().execute {
                MatchState.reset()
                MatchStartClient.reset()
                FreeKickAwardClient.clear()
                GoalkeeperStateClient.onMatchReset()
                PenaltyShootoutClient.reset()
                SetPieceClient.reset()
                SetPieceAreaViolationClient.clear()
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(PenaltyShootoutSyncS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                PenaltyShootoutClient.sync(
                    payload.active,
                    payload.penaltyScoreA,
                    payload.penaltyScoreB,
                    payload.suddenDeath,
                    payload.totalKicksTaken,
                    payload.currentKickerTeam,
                    payload.kickerName,
                    payload.currentKickerUuid,
                    payload.kickPhase,
                    payload.penaltyGoalTeam,
                    payload.activeDefendingTeam,
                    payload.firstKickTeam,
                    payload.ballGracePending,
                )
                if (payload.active && payload.ballGracePending) {
                    MatchStartClient.beginBallResetPending(payload.currentKickerTeam, SetPieceKind.PENALTY_KICK)
                } else if (payload.active && !payload.ballGracePending && payload.kickPhase == PenaltyKickPhase.SETUP) {
                    MatchStartClient.clearBallResetPending()
                }
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(PenaltyKickStartS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                if (!payload.scored) {
                    MatchStartClient.startPenaltyKick(payload.kickerTeam)
                }
                PenaltyKickClient.show(
                    payload.kickerTeam,
                    payload.kickerName,
                    payload.penaltyScoreA,
                    payload.penaltyScoreB,
                    payload.kickNumber,
                    payload.suddenDeath,
                    payload.teamAName,
                    payload.teamBName,
                    payload.scored,
                )
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(HalfKickoffS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                MatchState.teamAName = Component.literal(payload.teamAName)
                MatchState.teamBName = Component.literal(payload.teamBName)
                MatchStartClient.startHalfKickoff(
                    payload.kickoffTeam, payload.isKickoffTeam,
                    payload.phaseKey, payload.teamAName, payload.teamBName,
                )
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(MatchPauseS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                MatchState.isRunning = !payload.paused
                MatchPauseClient.show(payload.paused)
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(GoalLineOutS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                SetPiecePendingRestart.fromGoalLineOut(payload.outType, payload.restartTeam)
                GoalLineOutClient.show(
                    payload.outType,
                    payload.restartTeam,
                    payload.lastTouchPlayerName,
                    payload.lastTouchTeam,
                )
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(BallResetPendingS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                MatchStartClient.clearPenaltyFoulGoalWatch()
                MatchStartClient.beginBallResetPending(payload.restartTeam, payload.setPieceKind)
                if (payload.setPieceKind == SetPieceKind.PENALTY_KICK) {
                    FreeKickAwardClient.confirmPenaltyAward(payload.restartTeam)
                }
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(MatchResultS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                MatchResultClient.show(
                    payload.teamAScore,
                    payload.teamBScore,
                    payload.teamAName,
                    payload.teamBName,
                    payload.isDraw,
                    payload.wonByPenalties,
                    payload.penaltyScoreA,
                    payload.penaltyScoreB,
                    payload.penaltyWinner,
                )
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(MatchHudDebugS2CPayload.TYPE) { _, _ ->
            Minecraft.getInstance().execute {
                MatchHudDebugClient.scheduleAllPreviews()
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(MatchTimerSyncS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                MatchState.timerTicks = payload.timerTicks
                MatchState.stoppageTimerTicks = payload.stoppageTimerTicks
                MatchState.currentPhase = payload.currentPhase
                MatchState.teamAScore = payload.teamAScore
                MatchState.teamBScore = payload.teamBScore
                MatchState.teamAName = Component.literal(payload.teamAName)
                MatchState.teamBName = Component.literal(payload.teamBName)
                MatchState.isRunning = payload.isRunning
                MatchState.dynamicStoppageTicks = payload.dynamicStoppageTicks
                val current = net.astrorbits.football.match.MatchConfigHolder.current
                net.astrorbits.football.match.MatchConfigHolder.syncFromServer(
                    current.copy(
                        teamAName = payload.teamAName,
                        teamBName = payload.teamBName,
                        rules = current.rules.copy(
                            halfTimeMinutes = payload.halfTimeMinutes,
                            stoppageTimeMaxMinutes = payload.stoppageTimeMaxMinutes,
                            extraTimeHalfMinutes = payload.extraTimeHalfMinutes,
                            enableStoppageTime = payload.enableStoppageTime,
                            enableExtraTime = payload.enableExtraTime,
                            enablePenaltyShootout = payload.enablePenaltyShootout,
                        ),
                        accessibility = current.accessibility.copy(
                            enableFootballPositionIndicator = payload.enableFootballPositionIndicator,
                        ),
                    ),
                )
            }
        }
    }
}
