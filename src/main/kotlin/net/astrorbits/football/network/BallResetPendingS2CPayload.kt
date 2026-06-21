package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.SetPieceKind
import net.astrorbits.football.match.TeamSide
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C：球延迟复位宽限开始（与 [MatchState.postGoalResetPending] 同步）。 */
data class BallResetPendingS2CPayload(
    val restartTeam: TeamSide,
    val setPieceKind: SetPieceKind,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<BallResetPendingS2CPayload> =
            CustomPacketPayload.Type(NMBCTFootball.id("ball_reset_pending"))
        val CODEC: StreamCodec<FriendlyByteBuf, BallResetPendingS2CPayload> = StreamCodec.composite(
            TeamSide.STREAM_CODEC, BallResetPendingS2CPayload::restartTeam,
            SetPieceKind.STREAM_CODEC, BallResetPendingS2CPayload::setPieceKind,
            ::BallResetPendingS2CPayload,
        )
    }
}
