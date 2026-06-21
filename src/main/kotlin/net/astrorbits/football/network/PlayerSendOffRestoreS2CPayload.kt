package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C：罚下结束，清除被罚下者底部回归倒计时 HUD。 */
class PlayerSendOffRestoreS2CPayload private constructor() : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<PlayerSendOffRestoreS2CPayload> =
            CustomPacketPayload.Type(NMBCTFootball.id("player_send_off_restore"))
        val INSTANCE = PlayerSendOffRestoreS2CPayload()
        val CODEC: StreamCodec<FriendlyByteBuf, PlayerSendOffRestoreS2CPayload> = StreamCodec.unit(INSTANCE)
    }
}
