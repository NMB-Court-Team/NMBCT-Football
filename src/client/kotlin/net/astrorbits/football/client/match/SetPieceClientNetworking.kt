package net.astrorbits.football.client.match

import net.astrorbits.football.client.SetPieceAreaViolationClient
import net.astrorbits.football.client.SetPieceClient
import net.astrorbits.football.network.SetPieceAreaViolationS2CPayload
import net.astrorbits.football.network.SetPieceRestartS2CPayload
import net.astrorbits.football.network.SetPieceStateS2CPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object SetPieceClientNetworking {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(SetPieceAreaViolationS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                if (payload.secondsRemaining <= 0 || payload.areaNameKey.isBlank()) {
                    SetPieceAreaViolationClient.clear()
                } else {
                    SetPieceAreaViolationClient.update(payload.areaNameKey, payload.secondsRemaining)
                }
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(SetPieceRestartS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                SetPieceRestartClient.show(payload.kind, payload.restartTeam)
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(SetPieceStateS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                SetPieceClient.sync(
                    payload.kind,
                    payload.restartTeam,
                    payload.goalKickPhase,
                    payload.goalKickPickerUuid,
                    payload.throwInTakerUuid,
                    payload.movementFrozen,
                    payload.ballPos,
                    payload.defendingSide,
                    payload.penaltyKickerUuid,
                )
            }
        }
    }
}
