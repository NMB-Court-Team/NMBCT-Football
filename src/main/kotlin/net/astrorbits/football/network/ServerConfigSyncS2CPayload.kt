package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.config.server.FootballServerConfig
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** 服务端向客户端下发当前服务端配置；[openEditor] 为 true 时打开 YACL 编辑界面。 */
data class ServerConfigSyncS2CPayload(
    val config: FootballServerConfig,
    val openEditor: Boolean = false,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ServerConfigSyncS2CPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ServerConfigSyncS2CPayload>(NMBCTFootball.id("server_config_sync"))

        val CODEC: StreamCodec<FriendlyByteBuf, ServerConfigSyncS2CPayload> = StreamCodec.composite(
            ByteBufCodecs.fromCodec(FootballServerConfig.CODEC),
            ServerConfigSyncS2CPayload::config,
            ByteBufCodecs.BOOL,
            ServerConfigSyncS2CPayload::openEditor,
            ::ServerConfigSyncS2CPayload,
        )
    }
}
