package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.SetPieceRestartKind
import net.astrorbits.football.match.TeamSide
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class SetPieceRestartS2CPayload(
    val kind: SetPieceRestartKind,
    val restartTeam: TeamSide,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<SetPieceRestartS2CPayload> =
            CustomPacketPayload.Type(NMBCTFootball.id("set_piece_restart"))
        val CODEC: StreamCodec<FriendlyByteBuf, SetPieceRestartS2CPayload> = StreamCodec.composite(
            SetPieceRestartKind.STREAM_CODEC, SetPieceRestartS2CPayload::kind,
            TeamSide.STREAM_CODEC, SetPieceRestartS2CPayload::restartTeam,
            ::SetPieceRestartS2CPayload,
        )
    }
}
