package net.astrorbits.football.client

import net.astrorbits.football.Football
import net.astrorbits.football.network.GoalkeeperRoleS2CPayload
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.player.LocalPlayer

object GoalkeeperStateClient {
    var isGoalkeeper: Boolean = false
        private set

    var isHoldingBall: Boolean = false
        private set

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(GoalkeeperRoleS2CPayload.TYPE) { payload, _ ->
            isGoalkeeper = payload.isGoalkeeper
            if (!isGoalkeeper) {
                isHoldingBall = false
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            updateHoldingState(client.player)
        }
    }

    private fun updateHoldingState(player: LocalPlayer?) {
        if (player == null || !isGoalkeeper) {
            isHoldingBall = false
            return
        }

        isHoldingBall = player.level().getEntitiesOfClass(
            Football::class.java,
            player.boundingBox.inflate(6.0),
        ).any { it.getHolderEntityId() == player.id }
    }
}
