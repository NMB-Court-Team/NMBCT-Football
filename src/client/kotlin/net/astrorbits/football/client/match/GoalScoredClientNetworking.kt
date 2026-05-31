package net.astrorbits.football.client.match

import net.astrorbits.football.client.StaminaClient
import net.astrorbits.football.network.GoalScoredS2CPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object GoalScoredClientNetworking {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(GoalScoredS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                StaminaClient.onGoalScored()
                GoalScoredClient.show(
                    payload.scoringTeam, payload.scorerName, payload.scorerTeam,
                    payload.teamAScore, payload.teamBScore,
                    payload.ownGoal,
                )
            }
        }
    }
}
