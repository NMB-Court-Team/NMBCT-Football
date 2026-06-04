package net.astrorbits.football.client.match

import net.astrorbits.football.client.GoalkeeperStateClient
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.network.KickoffBallTouchedS2CPayload
import net.astrorbits.football.network.MatchStartS2CPayload
import net.astrorbits.football.network.HalfKickoffS2CPayload
import net.astrorbits.football.network.MatchResetS2CPayload
import net.astrorbits.football.network.GoalLineOutS2CPayload
import net.astrorbits.football.network.MatchResultS2CPayload
import net.astrorbits.football.network.MatchHudDebugS2CPayload
import net.astrorbits.football.network.MatchTimerSyncS2CPayload
import net.astrorbits.football.network.PenaltyKickStartS2CPayload
import net.astrorbits.football.network.PenaltyShootoutSyncS2CPayload
import net.astrorbits.football.network.PostGoalKickoffS2CPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object MatchStartClientNetworking {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(MatchStartS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                MatchState.teamAName = Component.literal(payload.teamAName)
                MatchState.teamBName = Component.literal(payload.teamBName)
                MatchState.teamAScore = 0
                MatchState.teamBScore = 0
                MatchStartClient.startMatch(
                    payload.playerTeam, payload.isGk, payload.kickoffTeam,
                    payload.teamAName, payload.teamBName,
                )
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
                GoalkeeperStateClient.onMatchReset()
                PenaltyShootoutClient.reset()
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
                    payload.kickPhase,
                    payload.activeDefendingTeam,
                    payload.firstKickTeam,
                )
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(PenaltyKickStartS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                PenaltyKickClient.show(
                    payload.kickerTeam,
                    payload.kickerName,
                    payload.penaltyScoreA,
                    payload.penaltyScoreB,
                    payload.kickNumber,
                    payload.suddenDeath,
                    payload.teamAName,
                    payload.teamBName,
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
        ClientPlayNetworking.registerGlobalReceiver(GoalLineOutS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                GoalLineOutClient.show(
                    payload.outType,
                    payload.restartTeam,
                    payload.lastTouchPlayerName,
                    payload.lastTouchTeam,
                )
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
