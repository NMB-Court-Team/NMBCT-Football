package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class FootballActionC2SPayload(
    val action: FootballActionType,
    val chargeRatio: Float,
    val chargeHeldMs: Long,
    val flags: Int,
    /** 客户端动作瞬间的 yaw，用于服务端尚未同步的朝向（如守门员持球开球）。 */
    val lookYaw: Float,
    /** 客户端动作瞬间的 pitch。 */
    val lookPitch: Float,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<FootballActionC2SPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<FootballActionC2SPayload>(NMBCTFootball.id("football_action"))

        val CODEC: StreamCodec<FriendlyByteBuf, FootballActionC2SPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeByte(payload.action.ordinal)
                buf.writeFloat(payload.chargeRatio)
                buf.writeVarLong(payload.chargeHeldMs)
                buf.writeVarInt(payload.flags)
                buf.writeFloat(payload.lookYaw)
                buf.writeFloat(payload.lookPitch)
            },
            { buf ->
                val action = FootballActionType.entries[buf.readByte().toInt().coerceIn(0, FootballActionType.entries.size - 1)]
                FootballActionC2SPayload(
                    action = action,
                    chargeRatio = buf.readFloat(),
                    chargeHeldMs = buf.readVarLong(),
                    flags = buf.readVarInt(),
                    lookYaw = buf.readFloat(),
                    lookPitch = buf.readFloat(),
                )
            }
        )
    }
}
