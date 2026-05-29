package net.astrorbits.football.util

import kotlin.math.exp
import net.astrorbits.football.config.server.KickChargeSettings

object KickChargeUtil {
    /** 过头蓄力衰减下限（满格为 1.0）。 */
    private const val DECAY_FLOOR = 0.6f

    enum class Phase {        NONE,
        RISING,
        PERFECT,
        DECAYING,
    }

    fun computeRatio(heldMs: Long, settings: KickChargeSettings): Float {
        if (heldMs < settings.chargeMinMs) {
            return 0f
        }

        return when {
            heldMs < settings.riseEndMs -> {
                val riseMs = heldMs - settings.chargeMinMs
                (riseMs.toFloat() / settings.chargeRiseMs.toFloat()).coerceIn(0f, 1f)
            }
            heldMs < settings.perfectEndMs -> 1f
            else -> {
                val overMs = heldMs - settings.perfectEndMs
                val tau = settings.chargeDecayMs.toFloat().coerceAtLeast(1f)
                DECAY_FLOOR + (1f - DECAY_FLOOR) * exp(-overMs / tau)
            }
        }
    }

    fun computePhase(heldMs: Long, settings: KickChargeSettings): Phase {
        if (heldMs < settings.chargeMinMs) {
            return Phase.NONE
        }
        return when {
            heldMs < settings.riseEndMs -> Phase.RISING
            heldMs < settings.perfectEndMs -> Phase.PERFECT
            else -> Phase.DECAYING
        }
    }

    fun isPerfectCharge(heldMs: Long, settings: KickChargeSettings): Boolean =
        computePhase(heldMs, settings) == Phase.PERFECT

    fun isCharging(heldMs: Long, settings: KickChargeSettings): Boolean =
        computePhase(heldMs, settings) != Phase.NONE
}
