package net.astrorbits.football.client.match

import net.astrorbits.football.client.SetPieceAreaViolationClient
import net.astrorbits.football.client.SetPieceClient
import net.astrorbits.football.match.MatchPhase
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.PenaltyKickPhase
import net.astrorbits.football.match.SetPieceKind
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
                SetPiecePendingRestart.fromRestartAward(payload.kind, payload.restartTeam)
                SetPieceRestartClient.show(
                    payload.kind,
                    payload.restartTeam,
                    payload.reasonKey,
                    payload.foulingPlayerName,
                    payload.foulingTeam,
                )
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
                    payload.penaltyKickPhase,
                    payload.freeKickType,
                    payload.freeKickTakerUuid,
                    payload.cornerKickTakerUuid,
                )
                syncMatchPenaltyKickIntro(payload)
                syncThrowInBallReady(payload)
            }
        }
    }

    /** 界外球主罚已持球就位时，球不在延迟复位流程中。 */
    private fun syncThrowInBallReady(payload: SetPieceStateS2CPayload) {
        if (payload.kind != SetPieceKind.THROW_IN) return
        if (payload.throwInTakerUuid == null || !payload.movementFrozen) return
        MatchStartClient.clearBallResetPending()
    }

    /** 正赛犯规点球：与点球大战不同，不发送 [PenaltyKickStartS2CPayload]，在此启动开球倒计时。 */
    private fun syncMatchPenaltyKickIntro(payload: SetPieceStateS2CPayload) {
        if (payload.kind != SetPieceKind.PENALTY_KICK) return
        if (payload.penaltyKickPhase != PenaltyKickPhase.SETUP) return
        if (MatchState.currentPhase == MatchPhase.PENALTIES) return
        val kickerTeam = payload.restartTeam ?: return
        MatchStartClient.clearPenaltyFoulGoalWatch()
        MatchStartClient.clearBallResetPending()
        MatchStartClient.startPenaltyKick(kickerTeam)
    }
}
