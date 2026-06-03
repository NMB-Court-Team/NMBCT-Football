package net.astrorbits.football.config.server

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder

/** 与具体动作绑定的体力消耗/倍率（守门员蓄力、滑铲、加速疾跑等）。 */
data class StaminaActionCostsSettings(
    val gkDiveFullChargeHoldDrainDelayTicks: Int = 20,
    val gkDiveFullChargeHoldDrainPerSecond: Float = 40f,
    val gkDiveChargeCancelCost: Float = 60f,
    val slideTackleEntryCost: Float = 60f,
    val slideTackleSustainCost: Float = 60f,
    val slideTackleMaxTotalCost: Float = 120f,
    val boostSprintStaminaDrainMultiplier: Float = 3f,
    val boostSprintSpeedMultiplier: Float = 2f,
) {
    fun gkDiveFullChargeHoldDrainPerTick(): Float =
        gkDiveFullChargeHoldDrainPerSecond / StaminaMechanismSettings.TICKS_PER_SECOND

    companion object {
        val CODEC: Codec<StaminaActionCostsSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.INT.optionalFieldOf("gk_dive_full_charge_hold_drain_delay_ticks", 20)
                    .forGetter(StaminaActionCostsSettings::gkDiveFullChargeHoldDrainDelayTicks),
                Codec.FLOAT.optionalFieldOf("gk_dive_full_charge_hold_drain_per_second", 40f)
                    .forGetter(StaminaActionCostsSettings::gkDiveFullChargeHoldDrainPerSecond),
                Codec.FLOAT.optionalFieldOf("gk_dive_charge_cancel_cost", 60f)
                    .forGetter(StaminaActionCostsSettings::gkDiveChargeCancelCost),
                Codec.FLOAT.optionalFieldOf("slide_tackle_entry_cost", 60f)
                    .forGetter(StaminaActionCostsSettings::slideTackleEntryCost),
                Codec.FLOAT.optionalFieldOf("slide_tackle_sustain_cost", 60f)
                    .forGetter(StaminaActionCostsSettings::slideTackleSustainCost),
                Codec.FLOAT.optionalFieldOf("slide_tackle_max_total_cost", 120f)
                    .forGetter(StaminaActionCostsSettings::slideTackleMaxTotalCost),
                Codec.FLOAT.optionalFieldOf("boost_sprint_stamina_drain_multiplier", 3f)
                    .forGetter(StaminaActionCostsSettings::boostSprintStaminaDrainMultiplier),
                Codec.FLOAT.optionalFieldOf("boost_sprint_speed_multiplier", 2f)
                    .forGetter(StaminaActionCostsSettings::boostSprintSpeedMultiplier),
            ).apply(i, ::StaminaActionCostsSettings)
        }.validate(::validate)

        val DEFAULT = StaminaActionCostsSettings()

        fun validate(costs: StaminaActionCostsSettings): DataResult<StaminaActionCostsSettings> {
            if (costs.gkDiveFullChargeHoldDrainDelayTicks !in 0..200) {
                return DataResult.error {
                    "gk_dive_full_charge_hold_drain_delay_ticks must be in [0, 200], was ${costs.gkDiveFullChargeHoldDrainDelayTicks}"
                }
            }
            if (costs.gkDiveFullChargeHoldDrainPerSecond !in 0f..200f) {
                return DataResult.error {
                    "gk_dive_full_charge_hold_drain_per_second must be in [0, 200], was ${costs.gkDiveFullChargeHoldDrainPerSecond}"
                }
            }
            if (costs.gkDiveChargeCancelCost !in 0f..500f) {
                return DataResult.error {
                    "gk_dive_charge_cancel_cost must be in [0, 500], was ${costs.gkDiveChargeCancelCost}"
                }
            }
            if (costs.slideTackleEntryCost !in 0f..500f) {
                return DataResult.error {
                    "slide_tackle_entry_cost must be in [0, 500], was ${costs.slideTackleEntryCost}"
                }
            }
            if (costs.slideTackleSustainCost !in 0f..500f) {
                return DataResult.error {
                    "slide_tackle_sustain_cost must be in [0, 500], was ${costs.slideTackleSustainCost}"
                }
            }
            if (costs.slideTackleMaxTotalCost !in 0f..1000f) {
                return DataResult.error {
                    "slide_tackle_max_total_cost must be in [0, 1000], was ${costs.slideTackleMaxTotalCost}"
                }
            }
            if (costs.slideTackleEntryCost > costs.slideTackleMaxTotalCost) {
                return DataResult.error {
                    "slide_tackle_entry_cost must be <= slide_tackle_max_total_cost"
                }
            }
            if (costs.slideTackleEntryCost + costs.slideTackleSustainCost > costs.slideTackleMaxTotalCost + 1e-3f) {
                return DataResult.error {
                    "slide_tackle_entry_cost + slide_tackle_sustain_cost must be <= slide_tackle_max_total_cost"
                }
            }
            if (costs.boostSprintStaminaDrainMultiplier !in 1f..10f) {
                return DataResult.error {
                    "boost_sprint_stamina_drain_multiplier must be in [1, 10], was ${costs.boostSprintStaminaDrainMultiplier}"
                }
            }
            if (costs.boostSprintSpeedMultiplier !in 1f..5f) {
                return DataResult.error {
                    "boost_sprint_speed_multiplier must be in [1, 5], was ${costs.boostSprintSpeedMultiplier}"
                }
            }
            return DataResult.success(costs)
        }
    }
}
