package net.astrorbits.football.client.match

import net.astrorbits.football.network.InvalidGoalS2CPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object InvalidGoalClientNetworking {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(InvalidGoalS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                InvalidGoalClient.show(
                    payload.scorerName,
                    payload.scorerTeam,
                    payload.teamAScore,
                    payload.teamBScore,
                    payload.teamAName,
                    payload.teamBName,
                )
            }
        }
    }
}
