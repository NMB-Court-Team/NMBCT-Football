package net.astrorbits.football.client.match

import net.astrorbits.football.network.KickoffBallTouchedS2CPayload
import net.astrorbits.football.network.MatchStartS2CPayload
import net.astrorbits.football.network.HalfKickoffS2CPayload
import net.astrorbits.football.network.MatchResetS2CPayload
import net.astrorbits.football.client.StaminaClient
import net.astrorbits.football.client.match.GoalLineOutClient
import net.astrorbits.football.network.GoalLineOutS2CPayload
import net.astrorbits.football.network.MatchResultS2CPayload
import net.astrorbits.football.network.PostGoalKickoffS2CPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object MatchStartClientNetworking {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(MatchStartS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                StaminaClient.onMatchStart()
                MatchStartClient.startMatch(
                    payload.playerTeam, payload.isGk, payload.kickoffTeam,
                    payload.teamAName, payload.teamBName,
                )
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(PostGoalKickoffS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                MatchStartClient.startPostGoalKickoff(payload.kickoffTeam, payload.playerTeam)
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(KickoffBallTouchedS2CPayload.TYPE) { _, _ ->
            Minecraft.getInstance().execute {
                MatchStartClient.onBallTouched()
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(MatchResetS2CPayload.TYPE) { _, _ ->
            Minecraft.getInstance().execute {
                MatchStartClient.reset()
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(HalfKickoffS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                StaminaClient.onHalfSwitch()
                MatchStartClient.startHalfKickoff(
                    payload.kickoffTeam, payload.playerTeam,
                    payload.phaseKey, payload.teamAName, payload.teamBName,
                )
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(GoalLineOutS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                GoalLineOutClient.show(payload.outType, payload.restartTeam)
                MatchStartClient.startGoalLineOutKickoff(payload.restartTeam, payload.playerTeam)
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(MatchResultS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                net.astrorbits.football.client.match.MatchResultClient.show(
                    payload.teamAScore, payload.teamBScore,
                    payload.teamAName, payload.teamBName,
                    payload.isDraw,
                )
            }
        }
    }
}
