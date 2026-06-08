package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.SetPieceRestartKind
import net.astrorbits.football.match.TeamSide
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class SetPieceRestartS2CPayload(
    val kind: SetPieceRestartKind,
    val restartTeam: TeamSide,
    val reasonKey: String,
    val foulingPlayerName: String,
    val foulingTeam: TeamSide?,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<SetPieceRestartS2CPayload> =
            CustomPacketPayload.Type(NMBCTFootball.id("set_piece_restart"))
        val CODEC: StreamCodec<FriendlyByteBuf, SetPieceRestartS2CPayload> = StreamCodec.of(
            { buf, payload ->
                SetPieceRestartKind.STREAM_CODEC.encode(buf, payload.kind)
                TeamSide.STREAM_CODEC.encode(buf, payload.restartTeam)
                buf.writeUtf(payload.reasonKey)
                buf.writeUtf(payload.foulingPlayerName)
                buf.writeBoolean(payload.foulingTeam != null)
                payload.foulingTeam?.let { TeamSide.STREAM_CODEC.encode(buf, it) }
            },
            { buf ->
                val kind = SetPieceRestartKind.STREAM_CODEC.decode(buf)
                val restartTeam = TeamSide.STREAM_CODEC.decode(buf)
                val reasonKey = buf.readUtf()
                val foulingPlayerName = buf.readUtf()
                val foulingTeam = if (buf.readBoolean()) TeamSide.STREAM_CODEC.decode(buf) else null
                SetPieceRestartS2CPayload(kind, restartTeam, reasonKey, foulingPlayerName, foulingTeam)
            },
        )
    }
}
