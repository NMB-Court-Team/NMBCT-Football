package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** C2S: 客户端请求半场开球（下半场/加时开始） */
class HalfKickoffRequestC2SPayload private constructor() : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<HalfKickoffRequestC2SPayload> = CustomPacketPayload.Type(NMBCTFootball.id("half_kickoff_request"))
        val INSTANCE = HalfKickoffRequestC2SPayload()
        val CODEC: StreamCodec<FriendlyByteBuf, HalfKickoffRequestC2SPayload> = StreamCodec.unit(INSTANCE)
    }
}
