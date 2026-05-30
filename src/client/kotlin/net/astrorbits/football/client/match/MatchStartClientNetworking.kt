package net.astrorbits.football.client.match

import net.astrorbits.football.network.KickoffBallTouchedS2CPayload
import net.astrorbits.football.network.MatchStartS2CPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object MatchStartClientNetworking {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(MatchStartS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                MatchStartClient.start(
                    payload.playerTeam, payload.isGk, payload.kickoffTeam,
                    payload.teamAName, payload.teamBName,
                )
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(KickoffBallTouchedS2CPayload.TYPE) { _, _ ->
            Minecraft.getInstance().execute {
                MatchStartClient.onBallTouched()
            }
        }
    }
}
