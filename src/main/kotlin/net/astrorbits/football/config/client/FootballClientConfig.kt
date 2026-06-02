package net.astrorbits.football.config.client

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

enum class GoalNetRenderMode(
    val serializedName: String,
    val translationKey: String,
) {
    AUTO("auto", "yacl3.config.nmbct-football.client.goal_net_render_mode.auto"),
    VANILLA_COMPAT("vanilla_compat", "yacl3.config.nmbct-football.client.goal_net_render_mode.vanilla_compat"),
    SHADER_COMPAT("shader_compat", "yacl3.config.nmbct-football.client.goal_net_render_mode.shader_compat");

    companion object {
        fun fromSerializedName(name: String): GoalNetRenderMode =
            entries.firstOrNull { it.serializedName == name } ?: AUTO

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
    /**
     * 足球的最远渲染距离（方块）。超过该距离后将不再绘制足球。
     */
    val ballRenderDist: Double = 128.0,
    /**
     * 面片切换比例（0~1）：面片切换距离 = [ballRenderDist] × 该值。
     * 例如 0.62 表示在最远渲染距离的 62% 处开始使用面片渲染。
     */
    val ballBillboardRatio: Double = 0.62,
    val dribbleHoldPacketInterval: Int = 2,
    val goalNetRenderMode: GoalNetRenderMode = GoalNetRenderMode.AUTO,
) {
    companion object {
        val CODEC: Codec<FootballClientConfig> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.DOUBLE.fieldOf("hint_hide_extra_range").forGetter(FootballClientConfig::hintHideExtraRange),
                Codec.DOUBLE.fieldOf("render_stationary_speed_sqr").forGetter(FootballClientConfig::renderStationarySpeedSqr),
                Codec.DOUBLE.fieldOf("client_correction_threshold").forGetter(FootballClientConfig::clientCorrectionThreshold),
                Codec.DOUBLE.optionalFieldOf("ball_render_dist", 128.0).forGetter(FootballClientConfig::ballRenderDist),
                Codec.DOUBLE.optionalFieldOf("ball_billboard_ratio", 0.62).forGetter(FootballClientConfig::ballBillboardRatio),
                Codec.INT.fieldOf("dribble_hold_packet_interval").forGetter(FootballClientConfig::dribbleHoldPacketInterval),
                GoalNetRenderMode.CODEC.optionalFieldOf("goal_net_render_mode", GoalNetRenderMode.AUTO)
                    .forGetter(FootballClientConfig::goalNetRenderMode),
            ).apply(instance, ::FootballClientConfig)
        }

        val DEFAULT = FootballClientConfig()
    }
}
