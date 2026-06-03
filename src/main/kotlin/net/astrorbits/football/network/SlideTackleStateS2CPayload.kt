package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class SlideTackleStateS2CPayload(
    val entityId: Int,
    val sliding: Boolean,
    /** 滑铲冷却结束的游戏 tick；0 表示无冷却或未同步。 */
    val cooldownUntilTick: Long = 0L,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<SlideTackleStateS2CPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<SlideTackleStateS2CPayload>(NMBCTFootball.id("slide_tackle_state"))

        val CODEC: StreamCodec<FriendlyByteBuf, SlideTackleStateS2CPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeVarInt(payload.entityId)
                buf.writeBoolean(payload.sliding)
                buf.writeVarLong(payload.cooldownUntilTick)
            },
            { buf ->
                SlideTackleStateS2CPayload(
                    entityId = buf.readVarInt(),
                    sliding = buf.readBoolean(),
                    cooldownUntilTick = buf.readVarLong(),
                )
            },
        )
    }
}
