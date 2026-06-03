package net.astrorbits.football.config.server

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder
import kotlin.math.abs

data class StaminaSpeedTier(
    val staminaFraction: Float,
    val speedMultiplier: Float,
) {
    companion object {
        val CODEC: Codec<StaminaSpeedTier> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.FLOAT.fieldOf("stamina_fraction").forGetter(StaminaSpeedTier::staminaFraction),
                Codec.FLOAT.fieldOf("speed_multiplier").forGetter(StaminaSpeedTier::speedMultiplier),
            ).apply(i, ::StaminaSpeedTier)
        }
    }
}

/**
 * 体力机制配置：消耗/回复速率、移速档位、比赛事件回复比例等。
 * 移速档位为「上限阈值」：当前体力比例 **严格小于** [StaminaSpeedTier.staminaFraction] 时采用对应倍率；
 * 若无 100% 档位则满体力隐含倍率 1.0。
 */
data class StaminaMechanismSettings(
    val maxStamina: Float = 1000f,
    val jumpCost: Float = 60f,
    val sprintDrainPerSecond: Float = 10f,
    val recoveryDelaySeconds: Float = 1f,
    val recoveryPerSecond: Float = 20f,
    val speedTiers: List<StaminaSpeedTier> = DEFAULT_SPEED_TIERS,
    val halfTimeRecoveryFraction: Float = 0.6f,
    val goalRecoveryFraction: Float = 0.15f,
    val actionCosts: StaminaActionCostsSettings = StaminaActionCostsSettings.DEFAULT,
) {
    val recoveryDelayTicks: Int
        get() = (recoveryDelaySeconds * TICKS_PER_SECOND).toInt()

    val gkDiveFullChargeHoldDrainDelayTicks get() = actionCosts.gkDiveFullChargeHoldDrainDelayTicks
    val gkDiveFullChargeHoldDrainPerSecond get() = actionCosts.gkDiveFullChargeHoldDrainPerSecond
    val gkDiveChargeCancelCost get() = actionCosts.gkDiveChargeCancelCost
    val slideTackleEntryCost get() = actionCosts.slideTackleEntryCost
    val slideTackleSustainCost get() = actionCosts.slideTackleSustainCost
    val slideTackleMaxTotalCost get() = actionCosts.slideTackleMaxTotalCost
    val boostSprintStaminaDrainMultiplier get() = actionCosts.boostSprintStaminaDrainMultiplier
    val boostSprintSpeedMultiplier get() = actionCosts.boostSprintSpeedMultiplier

    fun gkDiveFullChargeHoldDrainPerTick(): Float = actionCosts.gkDiveFullChargeHoldDrainPerTick()

    /** 按阈值升序排列，供查表与校验使用。 */
    fun sortedSpeedTiers(): List<StaminaSpeedTier> =
        speedTiers.sortedBy { it.staminaFraction }

    /**
     * 根据当前体力（0..[maxStamina]）计算移速倍率。
     * @see StaminaMechanismSettings
     */
    fun speedMultiplierForStamina(stamina: Float): Float {
        if (maxStamina <= 0f) {
            return 1f
        }
        if (stamina <= 0f) {
            val zeroTier = sortedSpeedTiers().firstOrNull { it.staminaFraction <= 0f }
            if (zeroTier != null) {
                return zeroTier.speedMultiplier
            }
        }
        val ratio = (stamina / maxStamina).coerceIn(0f, 1f)
        for (tier in sortedSpeedTiers()) {
            if (ratio < tier.staminaFraction) {
                return tier.speedMultiplier
            }
        }
        val fullTier = sortedSpeedTiers().firstOrNull { abs(it.staminaFraction - 1f) < 1e-4f }
        return fullTier?.speedMultiplier ?: 1f
    }

    fun halfTimeRecoveryAmount(): Float = maxStamina * halfTimeRecoveryFraction

    fun goalRecoveryAmount(): Float = maxStamina * goalRecoveryFraction

    companion object {
        const val TICKS_PER_SECOND = 20f

        val DEFAULT_SPEED_TIERS: List<StaminaSpeedTier> = listOf(
            StaminaSpeedTier(0f, 0.6f),
            StaminaSpeedTier(0.1f, 0.7f),
            StaminaSpeedTier(0.4f, 0.85f),
            StaminaSpeedTier(0.8f, 0.95f),
        )

        val CODEC: Codec<StaminaMechanismSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.FLOAT.optionalFieldOf("max_stamina", 1000f).forGetter(StaminaMechanismSettings::maxStamina),
                Codec.FLOAT.optionalFieldOf("jump_cost", 60f).forGetter(StaminaMechanismSettings::jumpCost),
                Codec.FLOAT.optionalFieldOf("sprint_drain_per_second", 10f)
                    .forGetter(StaminaMechanismSettings::sprintDrainPerSecond),
                Codec.FLOAT.optionalFieldOf("recovery_delay_seconds", 1f)
                    .forGetter(StaminaMechanismSettings::recoveryDelaySeconds),
                Codec.FLOAT.optionalFieldOf("recovery_per_second", 20f)
                    .forGetter(StaminaMechanismSettings::recoveryPerSecond),
                Codec.list(StaminaSpeedTier.CODEC).optionalFieldOf("speed_tiers", DEFAULT_SPEED_TIERS)
                    .forGetter(StaminaMechanismSettings::speedTiers),
                Codec.FLOAT.optionalFieldOf("half_time_recovery_fraction", 0.6f)
                    .forGetter(StaminaMechanismSettings::halfTimeRecoveryFraction),
                Codec.FLOAT.optionalFieldOf("goal_recovery_fraction", 0.15f)
                    .forGetter(StaminaMechanismSettings::goalRecoveryFraction),
                StaminaActionCostsSettings.CODEC.optionalFieldOf("action_costs", StaminaActionCostsSettings.DEFAULT)
                    .forGetter(StaminaMechanismSettings::actionCosts),
            ).apply(i, ::StaminaMechanismSettings)
        }.validate(::validate)

        fun validate(settings: StaminaMechanismSettings): DataResult<StaminaMechanismSettings> {
            if (settings.maxStamina !in 50f..5000f) {
                return DataResult.error { "max_stamina must be in [50, 5000], was ${settings.maxStamina}" }
            }
            if (settings.jumpCost !in 0f..200f) {
                return DataResult.error { "jump_cost must be in [0, 200], was ${settings.jumpCost}" }
            }
            if (settings.sprintDrainPerSecond !in 0f..50f) {
                return DataResult.error {
                    "sprint_drain_per_second must be in [0, 50], was ${settings.sprintDrainPerSecond}"
                }
            }
            if (settings.recoveryDelaySeconds !in 0.05f..5f) {
                return DataResult.error {
                    "recovery_delay_seconds must be in [0.05, 5], was ${settings.recoveryDelaySeconds}"
                }
            }
            if (settings.recoveryPerSecond !in 0f..100f) {
                return DataResult.error {
                    "recovery_per_second must be in [0, 100], was ${settings.recoveryPerSecond}"
                }
            }
            if (settings.halfTimeRecoveryFraction !in 0f..1f) {
                return DataResult.error {
                    "half_time_recovery_fraction must be in [0, 1], was ${settings.halfTimeRecoveryFraction}"
                }
            }
            if (settings.goalRecoveryFraction !in 0f..1f) {
                return DataResult.error {
                    "goal_recovery_fraction must be in [0, 1], was ${settings.goalRecoveryFraction}"
                }
            }
            val actionErr = validateActionCosts(settings.actionCosts).error()
            if (actionErr.isPresent) {
                return DataResult.error { actionErr.get().message() }
            }
            val optimizedTiers = optimizeSpeedTiers(settings.speedTiers)
            return validateSpeedTiers(optimizedTiers).map {
                settings.copy(speedTiers = optimizedTiers)
            }
        }

        /**
         * 合并按 [StaminaSpeedTier.staminaFraction] 升序排列后、相邻且移速倍率相同的档位。
         * 保留同组中最大的体力比例阈值（上限阈值语义下与合并前查表结果一致）。
         */
        fun optimizeSpeedTiers(tiers: List<StaminaSpeedTier>): List<StaminaSpeedTier> {
            if (tiers.size <= 1) {
                return tiers
            }
            val sorted = tiers.sortedBy { it.staminaFraction }
            val merged = ArrayList<StaminaSpeedTier>(sorted.size)
            var start = 0
            while (start < sorted.size) {
                var end = start
                while (end + 1 < sorted.size &&
                    speedMultipliersEqual(sorted[end + 1].speedMultiplier, sorted[start].speedMultiplier)
                ) {
                    end++
                }
                val kept = sorted.subList(start, end + 1).maxBy { it.staminaFraction }
                merged.add(kept)
                start = end + 1
            }
            return merged
        }

        private fun speedMultipliersEqual(a: Float, b: Float): Boolean = abs(a - b) < 1e-4f

        private fun validateActionCosts(costs: StaminaActionCostsSettings): DataResult<Unit> =
            StaminaActionCostsSettings.validate(costs).map { Unit }

        fun validateSpeedTiers(tiers: List<StaminaSpeedTier>): DataResult<Unit> {
            val fractions = mutableSetOf<Float>()
            var lastFraction = Float.NEGATIVE_INFINITY
            var lastMultiplier = Float.NEGATIVE_INFINITY
            for (tier in tiers.sortedBy { it.staminaFraction }) {
                if (tier.staminaFraction !in 0f..1f) {
                    return DataResult.error {
                        "stamina_fraction must be in [0, 1], was ${tier.staminaFraction}"
                    }
                }
                if (tier.speedMultiplier !in 0.1f..2f) {
                    return DataResult.error {
                        "speed_multiplier must be in [0.1, 2], was ${tier.speedMultiplier}"
                    }
                }
                if (!fractions.add(tier.staminaFraction)) {
                    return DataResult.error { "duplicate stamina_fraction ${tier.staminaFraction}" }
                }
                if (tier.staminaFraction < lastFraction - 1e-6f) {
                    return DataResult.error { "speed_tiers must be sorted by stamina_fraction" }
                }
                if (tier.speedMultiplier + 1e-6f < lastMultiplier) {
                    return DataResult.error {
                        "speed_multiplier must be non-decreasing as stamina_fraction increases " +
                            "(got ${tier.speedMultiplier} after $lastMultiplier)"
                    }
                }
                lastFraction = tier.staminaFraction
                lastMultiplier = tier.speedMultiplier
            }
            return DataResult.success(Unit)
        }
    }
}
