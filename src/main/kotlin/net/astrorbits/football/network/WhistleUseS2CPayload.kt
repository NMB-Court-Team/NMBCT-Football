package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** 玩家使用哨子：客户端在吹哨玩家实体上播放绑定音效。 */
data class WhistleUseS2CPayload(
    val entityId: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<WhistleUseS2CPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<WhistleUseS2CPayload>(NMBCTFootball.id("whistle_use"))

        val CODEC: StreamCodec<FriendlyByteBuf, WhistleUseS2CPayload> = StreamCodec.of(
            { buf, payload -> buf.writeVarInt(payload.entityId) },
            { buf -> WhistleUseS2CPayload(entityId = buf.readVarInt()) },
        )
    }
}
