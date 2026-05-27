package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class FootballActionC2SPayload(
    val action: FootballActionType,
    val chargeRatio: Float,
    val flags: Int
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<FootballActionC2SPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<FootballActionC2SPayload>(NMBCTFootball.id("football_action"))

        val CODEC: StreamCodec<FriendlyByteBuf, FootballActionC2SPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeByte(payload.action.ordinal)
                buf.writeFloat(payload.chargeRatio)
                buf.writeVarInt(payload.flags)
            },
            { buf ->
                val action = FootballActionType.entries[buf.readByte().toInt().coerceIn(0, FootballActionType.entries.size - 1)]
                FootballActionC2SPayload(
                    action = action,
                    chargeRatio = buf.readFloat(),
                    flags = buf.readVarInt()
                )
            }
        )
    }
}
