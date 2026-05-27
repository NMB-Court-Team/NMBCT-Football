package net.astrorbits.football.client.match

import net.astrorbits.football.match.MatchState
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

object MatchStateClient {
    fun registerTickEvent() {
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
    }

    fun tick(client: Minecraft) {
        if (MatchState.isRunning && client.level != null && !client.isPaused) {
            MatchState.timerTicks++
        }
    }
}