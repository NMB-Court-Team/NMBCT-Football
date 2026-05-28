package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** 同步守门员持球后的「禁止丢球/开球」剩余锁定 tick 数（0 表示已解除）。 */
data class GoalkeeperHoldLockS2CPayload(
    val lockTicksRemaining: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GoalkeeperHoldLockS2CPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<GoalkeeperHoldLockS2CPayload>(NMBCTFootball.id("goalkeeper_hold_lock"))

        val CODEC: StreamCodec<FriendlyByteBuf, GoalkeeperHoldLockS2CPayload> = StreamCodec.of(
            { buf, payload -> buf.writeVarInt(payload.lockTicksRemaining) },
            { buf -> GoalkeeperHoldLockS2CPayload(lockTicksRemaining = buf.readVarInt()) },
        )
    }
}
