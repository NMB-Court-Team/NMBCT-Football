package net.astrorbits.football.config.server

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/** 射门/长抛蓄力时机（服务端权威）。 */
data class KickChargeSettings(
    val tapMaxMs: Long = 250L,
    val chargeMinMs: Long = 300L,
    val chargeRiseMs: Long = 900L,
    val chargePerfectWindowMs: Long = 120L,
    val chargeDecayMs: Long = 1500L,
    /** 与原版弹射物 inaccuracy 同尺度；R 键传球/射门/发球的方向散布。 */
    val kickSpreadInaccuracy: Double = 1.5,
    /** 完美蓄力松开时的力度乘数（1.0 = 无加成）。 */
    val perfectChargeForceBonus: Double = 1.08,
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
            ).apply(i, ::KickChargeSettings)
        }

        val DEFAULT = KickChargeSettings()
    }
}
