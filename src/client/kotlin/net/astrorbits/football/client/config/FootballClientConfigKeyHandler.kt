package net.astrorbits.football.client.config

import net.astrorbits.football.client.config.yacl.FootballClientConfigScreen
import net.astrorbits.football.client.key.FootballKeyBindings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

object FootballClientConfigKeyHandler {
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (FootballKeyBindings.OPEN_CLIENT_CONFIG.consumeClick()) {
                client.setScreen(FootballClientConfigScreen.create(client.screen))
            }
        }
    }
}
