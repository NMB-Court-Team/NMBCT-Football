package net.astrorbits.football.config.server

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/** 射门/长抛蓄力时机（服务端权威）。 */
data class KickChargeSettings(
    val tapMaxMs: Long = 100L,
    val chargeMinMs: Long = 150L,
    val chargeRiseMs: Long = 750L,
    val chargePerfectWindowMs: Long = 30L,
    val chargeDecayMs: Long = 1350L,
    /** 与原版弹射物 inaccuracy 同尺度；R 键传球/射门/发球的方向散布。 */
    val kickSpreadInaccuracy: Double = 1.5,
    /** 完美蓄力松开时的力度乘数（1.0 = 无加成）。 */
    val perfectChargeForceBonus: Double = 1.08,
    /** 射门后视角拖尾窗口（毫秒），用于弧线球输入。 */
    val curveWindowMs: Long = 350L,
    /** 弧线输入：相对射门朝向的最大有效偏航（度）。 */
    val curveMaxYawDeg: Double = 42.0,
    /** 弧线输入：低于此偏航（度）忽略。 */
    val curveMinYawDeg: Double = 3.0,
    /** 弧线侧向速度上限（blocks/tick）。 */
    val curveMaxLateralSpeed: Double = 1.0,
    /** 蓄力比例低于此值时不启用弧线。 */
    val curveMinChargeRatio: Float = 0.12f,
    /** 窗口结束后侧向速度渐增到目标所需的 tick 数。 */
    val curveRampTicks: Long = 18L,
) {
    val riseEndMs: Long get() = chargeMinMs + chargeRiseMs
    val perfectEndMs: Long get() = riseEndMs + chargePerfectWindowMs

    companion object {
        val CODEC: Codec<KickChargeSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.LONG.fieldOf("tap_max_ms").forGetter(KickChargeSettings::tapMaxMs),
                Codec.LONG.fieldOf("charge_min_ms").forGetter(KickChargeSettings::chargeMinMs),
                Codec.LONG.fieldOf("charge_rise_ms").forGetter(KickChargeSettings::chargeRiseMs),
                Codec.LONG.fieldOf("charge_perfect_window_ms").forGetter(KickChargeSettings::chargePerfectWindowMs),
                Codec.LONG.fieldOf("charge_decay_ms").forGetter(KickChargeSettings::chargeDecayMs),
                Codec.DOUBLE.optionalFieldOf("kick_spread_inaccuracy", 1.5)
                    .forGetter(KickChargeSettings::kickSpreadInaccuracy),
                Codec.DOUBLE.optionalFieldOf("perfect_charge_force_bonus", 1.08)
                    .forGetter(KickChargeSettings::perfectChargeForceBonus),
                Codec.LONG.optionalFieldOf("curve_window_ms", 350L)
                    .forGetter(KickChargeSettings::curveWindowMs),
                Codec.DOUBLE.optionalFieldOf("curve_max_yaw_deg", 42.0)
                    .forGetter(KickChargeSettings::curveMaxYawDeg),
                Codec.DOUBLE.optionalFieldOf("curve_min_yaw_deg", 3.0)
                    .forGetter(KickChargeSettings::curveMinYawDeg),
                Codec.DOUBLE.optionalFieldOf("curve_max_lateral_speed", 0.55)
                    .forGetter(KickChargeSettings::curveMaxLateralSpeed),
                Codec.FLOAT.optionalFieldOf("curve_min_charge_ratio", 0.12f)
                    .forGetter(KickChargeSettings::curveMinChargeRatio),
                Codec.LONG.optionalFieldOf("curve_ramp_ticks", 18L)
                    .forGetter(KickChargeSettings::curveRampTicks),
            ).apply(i, ::KickChargeSettings)
        }

        val DEFAULT = KickChargeSettings()
    }
}
