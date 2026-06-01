package net.astrorbits.football.client.match

import net.astrorbits.football.client.StaminaClient
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.network.GoalScoredS2CPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object GoalScoredClientNetworking {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(GoalScoredS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                StaminaClient.onGoalScored()
                // 同步 MatchState 比分，保证左上角 HUD 与服务端一致
                MatchState.teamAScore = payload.teamAScore
                MatchState.teamBScore = payload.teamBScore
                GoalScoredClient.show(
                    payload.scoringTeam, payload.scorerName, payload.scorerTeam,
                    payload.teamAScore, payload.teamBScore,
                    payload.ownGoal,
                )
            }
        }
    }
}
