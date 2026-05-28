package net.astrorbits.football.config.client

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * 仅本机生效的客户端配置：HUD、输入手感、渲染与预测。
 */
data class FootballClientConfig(
    val hintHideExtraRange: Double = 0.4,
    val renderStationarySpeedSqr: Double = 1.0e-4,
    val clientCorrectionThreshold: Double = 0.25,
    val tapMaxMs: Long = 250L,
    val chargeMinMs: Long = 300L,
    val chargeMaxMs: Long = 1200L,
    val dribbleHoldPacketInterval: Int = 2,
) {
    companion object {
        val CODEC: Codec<FootballClientConfig> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.DOUBLE.fieldOf("hint_hide_extra_range").forGetter(FootballClientConfig::hintHideExtraRange),
                Codec.DOUBLE.fieldOf("render_stationary_speed_sqr").forGetter(FootballClientConfig::renderStationarySpeedSqr),
                Codec.DOUBLE.fieldOf("client_correction_threshold").forGetter(FootballClientConfig::clientCorrectionThreshold),
                Codec.LONG.fieldOf("tap_max_ms").forGetter(FootballClientConfig::tapMaxMs),
                Codec.LONG.fieldOf("charge_min_ms").forGetter(FootballClientConfig::chargeMinMs),
                Codec.LONG.fieldOf("charge_max_ms").forGetter(FootballClientConfig::chargeMaxMs),
                Codec.INT.fieldOf("dribble_hold_packet_interval").forGetter(FootballClientConfig::dribbleHoldPacketInterval),
            ).apply(instance, ::FootballClientConfig)
        }

        val DEFAULT = FootballClientConfig()
    }
}
