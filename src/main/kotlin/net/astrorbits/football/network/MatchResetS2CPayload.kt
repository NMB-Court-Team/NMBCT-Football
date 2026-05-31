package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 比赛重置，客户端清除开球锁定状态 */
class MatchResetS2CPayload private constructor() : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<MatchResetS2CPayload> = CustomPacketPayload.Type(NMBCTFootball.id("match_reset"))
        val INSTANCE = MatchResetS2CPayload()
        val CODEC: StreamCodec<FriendlyByteBuf, MatchResetS2CPayload> = StreamCodec.unit(INSTANCE)
    }
}
