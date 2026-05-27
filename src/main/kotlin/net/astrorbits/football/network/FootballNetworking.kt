package net.astrorbits.football.network

import net.astrorbits.football.input.FootballPlayerActions
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking

object FootballNetworking {
    fun registerPayloadType() {
        PayloadTypeRegistry.serverboundPlay().register(FootballActionPayload.TYPE, FootballActionPayload.CODEC)
    }

    fun registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(FootballActionPayload.TYPE) { payload, context ->
            context.server().execute {
                FootballPlayerActions.handle(context.player(), payload)
            }
        }
    }
}
