package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class BoostSprintToggleC2SPayload(
    val enabled: Boolean,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<BoostSprintToggleC2SPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<BoostSprintToggleC2SPayload>(NMBCTFootball.id("boost_sprint_toggle"))

        val CODEC: StreamCodec<FriendlyByteBuf, BoostSprintToggleC2SPayload> = StreamCodec.composite(
            ByteBufCodecs.BOOL, BoostSprintToggleC2SPayload::enabled,
            ::BoostSprintToggleC2SPayload,
        )
    }
}
