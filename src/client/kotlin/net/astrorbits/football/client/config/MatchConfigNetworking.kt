package net.astrorbits.football.client.config

import net.astrorbits.football.network.MatchConfigSyncS2CPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object MatchConfigNetworking {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(MatchConfigSyncS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                val parent = Minecraft.getInstance().screen
                Minecraft.getInstance().setScreen(
                    MatchConfigScreen(parent, payload.config),
                )
            }
        }
    }
}
