package net.astrorbits.football.config.client

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

enum class GoalNetRenderMode(
    val serializedName: String,
    val translationKey: String,
) {
    VANILLA_COMPAT("vanilla_compat", "yacl3.config.nmbct-football.client.goal_net_render_mode.vanilla_compat"),
    SHADER_COMPAT("shader_compat", "yacl3.config.nmbct-football.client.goal_net_render_mode.shader_compat");

    companion object {
        fun fromSerializedName(name: String): GoalNetRenderMode =
            entries.firstOrNull { it.serializedName == name } ?: SHADER_COMPAT

        val CODEC: Codec<GoalNetRenderMode> =
            Codec.STRING.xmap(::fromSerializedName, GoalNetRenderMode::serializedName)
    }
}

/**
 * 仅本机生效的客户端配置：HUD、输入手感、渲染与预测。
 */
data class FootballClientConfig(
    val hintHideExtraRange: Double = 0.4,
    val renderStationarySpeedSqr: Double = 1.0e-4,
    val clientCorrectionThreshold: Double = 0.25,
    val dribbleHoldPacketInterval: Int = 2,
    val goalNetRenderMode: GoalNetRenderMode = GoalNetRenderMode.SHADER_COMPAT,
) {
    companion object {
        val CODEC: Codec<FootballClientConfig> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.DOUBLE.fieldOf("hint_hide_extra_range").forGetter(FootballClientConfig::hintHideExtraRange),
                Codec.DOUBLE.fieldOf("render_stationary_speed_sqr").forGetter(FootballClientConfig::renderStationarySpeedSqr),
                Codec.DOUBLE.fieldOf("client_correction_threshold").forGetter(FootballClientConfig::clientCorrectionThreshold),
                Codec.INT.fieldOf("dribble_hold_packet_interval").forGetter(FootballClientConfig::dribbleHoldPacketInterval),
                GoalNetRenderMode.CODEC.optionalFieldOf("goal_net_render_mode", GoalNetRenderMode.SHADER_COMPAT)
                    .forGetter(FootballClientConfig::goalNetRenderMode),
            ).apply(instance, ::FootballClientConfig)
        }

        val DEFAULT = FootballClientConfig()
    }
}
