package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class FootballActionPayload(
    val action: FootballActionType,
    val chargeRatio: Float,
    val flags: Int
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<FootballActionPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<FootballActionPayload>(
            NMBCTFootball.id("football_action")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, FootballActionPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeByte(payload.action.ordinal)
                buf.writeFloat(payload.chargeRatio)
                buf.writeVarInt(payload.flags)
            },
            { buf ->
                val action = FootballActionType.entries[buf.readByte().toInt().coerceIn(0, FootballActionType.entries.size - 1)]
                FootballActionPayload(
                    action = action,
                    chargeRatio = buf.readFloat(),
                    flags = buf.readVarInt()
                )
            }
        )
    }
}
