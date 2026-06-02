package net.astrorbits.football.client.config

import net.astrorbits.football.client.config.yacl.FootballServerConfigScreen
import net.astrorbits.football.config.server.FootballServerConfigHolder
import net.astrorbits.football.network.ServerConfigSyncS2CPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object FootballConfigNetworking {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(ServerConfigSyncS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                FootballServerConfigHolder.syncFromServer(payload.config)
                if (payload.openEditor) {
                    val parent = Minecraft.getInstance().screen
                    Minecraft.getInstance().setScreen(
                        FootballServerConfigScreen.create(parent, payload.config),
                    )
                }
            }
        }
    }
}
