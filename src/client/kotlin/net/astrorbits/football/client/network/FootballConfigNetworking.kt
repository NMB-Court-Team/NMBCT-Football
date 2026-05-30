package net.astrorbits.football.client.network

import net.astrorbits.football.client.config.yacl.FootballServerConfigScreen
import net.astrorbits.football.network.ServerConfigSyncS2CPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object FootballConfigNetworking {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(ServerConfigSyncS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                val parent = Minecraft.getInstance().screen
                Minecraft.getInstance().setScreen(
                    FootballServerConfigScreen.create(parent, payload.config),
                )
            }
        }
    }
}
