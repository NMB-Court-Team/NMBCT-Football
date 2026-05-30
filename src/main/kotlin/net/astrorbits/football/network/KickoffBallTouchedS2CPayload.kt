package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 发球方已触球，非发球方解除锁定 */
class KickoffBallTouchedS2CPayload private constructor() : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<KickoffBallTouchedS2CPayload> = CustomPacketPayload.Type(NMBCTFootball.id("kickoff_ball_touched"))
        val INSTANCE = KickoffBallTouchedS2CPayload()
        val CODEC: StreamCodec<FriendlyByteBuf, KickoffBallTouchedS2CPayload> = StreamCodec.unit(INSTANCE)
    }
}
