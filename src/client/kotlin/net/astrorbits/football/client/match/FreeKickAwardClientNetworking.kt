package net.astrorbits.football.client.match

import net.astrorbits.football.network.FreeKickAwardS2CPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object FreeKickAwardClientNetworking {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(FreeKickAwardS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                FreeKickAwardClient.show(
                    payload.freeKickType,
                    payload.foulReason,
                    payload.foulingPlayerName,
                    payload.foulingTeam,
                    payload.restartTeam,
                )
            }
        }
    }
}
