package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** C2S: 客户端请求比赛结算（进入 FINISHED 阶段时） */
class MatchResultRequestC2SPayload private constructor() : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        val TYPE: CustomPacketPayload.Type<MatchResultRequestC2SPayload> = CustomPacketPayload.Type(NMBCTFootball.id("match_result_request"))
        val INSTANCE = MatchResultRequestC2SPayload()
        val CODEC: StreamCodec<FriendlyByteBuf, MatchResultRequestC2SPayload> = StreamCodec.unit(INSTANCE)
    }
}
