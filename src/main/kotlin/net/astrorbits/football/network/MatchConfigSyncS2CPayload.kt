package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.MatchConfig
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class MatchConfigSyncS2CPayload(
    val config: MatchConfig,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<MatchConfigSyncS2CPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<MatchConfigSyncS2CPayload>(NMBCTFootball.id("match_config_sync"))
        val CODEC: StreamCodec<FriendlyByteBuf, MatchConfigSyncS2CPayload> = StreamCodec.composite(
            ByteBufCodecs.fromCodec(MatchConfig.CODEC),
            MatchConfigSyncS2CPayload::config,
            ::MatchConfigSyncS2CPayload,
        )
    }
}
