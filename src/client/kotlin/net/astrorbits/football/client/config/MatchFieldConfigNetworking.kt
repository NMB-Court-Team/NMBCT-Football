package net.astrorbits.football.client.config

import net.astrorbits.football.network.MatchFieldConfigSyncS2CPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object MatchFieldConfigNetworking {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(MatchFieldConfigSyncS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                val parent = Minecraft.getInstance().screen
                Minecraft.getInstance().setScreen(
                    MatchFieldConfigScreen(parent, payload.config),
                )
            }
        }
    }
}
