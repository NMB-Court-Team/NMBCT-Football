package net.astrorbits.football.client.match

import net.astrorbits.football.match.FreeKickFoulReason
import net.astrorbits.football.match.FreeKickType
import net.astrorbits.football.network.FreeKickAwardS2CPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object FreeKickAwardClientNetworking {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(FreeKickAwardS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                if (payload.freeKickType == FreeKickType.PENALTY &&
                    payload.foulReason == FreeKickFoulReason.SLIDE_TACKLE_IN_PENALTY_AREA
                ) {
                    MatchStartClient.beginPenaltyFoulGoalWatch()
                    FreeKickAwardClient.showFoulOnly(
                        payload.foulReason,
                        payload.foulingPlayerName,
                        payload.foulingTeam,
                    )
                } else {
                    SetPiecePendingRestart.fromFreeKickAward(payload.freeKickType, payload.restartTeam)
                    FreeKickAwardClient.show(
                        payload.freeKickType,
                        payload.foulReason,
                        payload.foulingPlayerName,
                        payload.foulingTeam,
                        payload.restartTeam,
                    )
                }
            }
        }
    }
}
