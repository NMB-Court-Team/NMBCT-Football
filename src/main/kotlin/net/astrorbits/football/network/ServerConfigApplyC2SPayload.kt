package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.config.server.FootballServerConfig
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** 客户端将编辑后的服务端配置提交回服务端。 */
data class ServerConfigApplyC2SPayload(
    val config: FootballServerConfig,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ServerConfigApplyC2SPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ServerConfigApplyC2SPayload>(NMBCTFootball.id("server_config_apply"))

        val CODEC: StreamCodec<FriendlyByteBuf, ServerConfigApplyC2SPayload> = StreamCodec.composite(
            ByteBufCodecs.fromCodec(FootballServerConfig.CODEC),
            ServerConfigApplyC2SPayload::config,
            ::ServerConfigApplyC2SPayload,
        )
    }
}
