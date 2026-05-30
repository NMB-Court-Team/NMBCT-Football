package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.MatchConfig
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C：打开场地设置界面（球门 + 出生点） */
data class MatchFieldConfigSyncS2CPayload(
    val config: MatchConfig,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<MatchFieldConfigSyncS2CPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<MatchFieldConfigSyncS2CPayload>(NMBCTFootball.id("match_field_config_sync"))
        val CODEC: StreamCodec<FriendlyByteBuf, MatchFieldConfigSyncS2CPayload> = StreamCodec.composite(
            ByteBufCodecs.fromCodec(MatchConfig.CODEC),
            MatchFieldConfigSyncS2CPayload::config,
            ::MatchFieldConfigSyncS2CPayload,
        )
    }
}
