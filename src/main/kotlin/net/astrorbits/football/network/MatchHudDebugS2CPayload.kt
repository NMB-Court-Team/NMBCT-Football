package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 触发客户端依次预览比赛事件 HUD */
class MatchHudDebugS2CPayload private constructor() : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<MatchHudDebugS2CPayload> =
            CustomPacketPayload.Type(NMBCTFootball.id("match_hud_debug"))
        val INSTANCE = MatchHudDebugS2CPayload()
        val CODEC: StreamCodec<FriendlyByteBuf, MatchHudDebugS2CPayload> = StreamCodec.unit(INSTANCE)
    }
}
