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
    val openEditor: Boolean = true,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<MatchFieldConfigSyncS2CPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<MatchFieldConfigSyncS2CPayload>(NMBCTFootball.id("match_field_config_sync"))
        val CODEC: StreamCodec<FriendlyByteBuf, MatchFieldConfigSyncS2CPayload> = StreamCodec.of(
            { buf, payload ->
                ByteBufCodecs.fromCodec(MatchConfig.CODEC).encode(buf, payload.config)
                buf.writeBoolean(payload.openEditor)
            },
            { buf ->
                MatchFieldConfigSyncS2CPayload(
                    config = ByteBufCodecs.fromCodec(MatchConfig.CODEC).decode(buf),
                    openEditor = buf.readBoolean(),
                )
            },
        )
    }
}
