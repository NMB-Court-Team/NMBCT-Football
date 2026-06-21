package net.astrorbits.football.client.match

import net.astrorbits.football.network.PlayerSendOffRestoreS2CPayload
import net.astrorbits.football.network.PlayerSendOffS2CPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object PlayerSendOffClientNetworking {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(PlayerSendOffS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                SendOffBroadcastClient.show(payload.playerName, payload.team)
                val localUuid = Minecraft.getInstance().player?.uuid
                if (localUuid != null && localUuid == payload.sentOffPlayerUuid) {
                    SendOffRedCardClient.show(payload.team)
                    SendOffLocalClient.begin(payload.expireAtTimerTicks)
                }
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(PlayerSendOffRestoreS2CPayload.TYPE) { _, _ ->
            Minecraft.getInstance().execute {
                SendOffLocalClient.clear()
            }
        }
    }
}
