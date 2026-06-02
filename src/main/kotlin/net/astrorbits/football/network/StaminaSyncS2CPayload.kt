package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C：同步玩家当前体力与服务端最大体力（用于 HUD 与移速）。 */
data class StaminaSyncS2CPayload(
    val stamina: Float,
    val maxStamina: Float,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<StaminaSyncS2CPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<StaminaSyncS2CPayload>(NMBCTFootball.id("stamina_sync"))

        val CODEC: StreamCodec<FriendlyByteBuf, StaminaSyncS2CPayload> = StreamCodec.composite(
            ByteBufCodecs.FLOAT, StaminaSyncS2CPayload::stamina,
            ByteBufCodecs.FLOAT, StaminaSyncS2CPayload::maxStamina,
            ::StaminaSyncS2CPayload,
        )
    }
}
