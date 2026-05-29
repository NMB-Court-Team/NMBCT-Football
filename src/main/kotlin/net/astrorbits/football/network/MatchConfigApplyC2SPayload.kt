package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.MatchConfig
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class MatchConfigApplyC2SPayload(
    val config: MatchConfig,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<MatchConfigApplyC2SPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<MatchConfigApplyC2SPayload>(NMBCTFootball.id("match_config_apply"))
        val CODEC: StreamCodec<FriendlyByteBuf, MatchConfigApplyC2SPayload> = StreamCodec.composite(
            ByteBufCodecs.fromCodec(MatchConfig.CODEC),
            MatchConfigApplyC2SPayload::config,
            ::MatchConfigApplyC2SPayload,
        )
    }
}
