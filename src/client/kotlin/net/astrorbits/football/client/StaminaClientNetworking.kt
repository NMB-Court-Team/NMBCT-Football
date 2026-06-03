package net.astrorbits.football.client

import net.astrorbits.football.network.StaminaSyncS2CPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object StaminaClientNetworking {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(StaminaSyncS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                StaminaClient.applySync(payload.stamina, payload.maxStamina, payload.boostSprintActive)
            }
        }
    }
}
