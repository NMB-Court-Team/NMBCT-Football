package net.astrorbits.football.network

import net.astrorbits.football.input.FootballPlayerActions
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryFriendlyByteBuf

object FootballNetworking {
    fun registerPayloadType() {
        registerC2SPayloadType(PayloadTypeRegistry.serverboundPlay())
        registerS2CPayloadType(PayloadTypeRegistry.clientboundPlay())
    }

    private fun registerC2SPayloadType(registry: PayloadTypeRegistry<RegistryFriendlyByteBuf>) {
        registry.register(FootballActionC2SPayload.TYPE, FootballActionC2SPayload.CODEC)
    }

    private fun registerS2CPayloadType(registry: PayloadTypeRegistry<RegistryFriendlyByteBuf>) {
        // No S2C payloads for now
    }

    fun registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(FootballActionC2SPayload.TYPE) { payload, context ->
            context.server().execute {
                FootballPlayerActions.handle(context.player(), payload)
            }
        }
    }
}
