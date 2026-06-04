package net.astrorbits.football.input

import net.astrorbits.football.config.server.PlayerSlideTackleSettings

/** 滑铲音效节奏（20 TPS）：start 0.3s / loop 0.2s / end 提前 0.5s。 */
object SlideTackleSoundTiming {
    const val START_TICKS = 6L
    const val LOOP_INTERVAL_TICKS = 4L
    const val END_LEAD_TICKS = 10L
    const val MOVE_EPSILON = 0.001

    fun slideSpeedAtTick(settings: PlayerSlideTackleSettings, elapsed: Long, contactSpeedScale: Double): Double {
        val holdTicks = settings.initialHoldTicks.toLong()
        val decayTicks = settings.decayTicks.coerceAtLeast(1).toLong()
        val speed = if (elapsed < holdTicks) {
            settings.initialSpeed
        } else {
            val decayElapsed = elapsed - holdTicks
            val decayRatio = (decayElapsed.toDouble() / decayTicks.toDouble()).coerceIn(0.0, 1.0)
            settings.initialSpeed * (1.0 - decayRatio)
        }
        return speed * contactSpeedScale.coerceIn(0.0, 1.0)
    }

    fun remainingSlideMovementTicks(
        settings: PlayerSlideTackleSettings,
        elapsed: Long,
        contactSpeedScale: Double,
    ): Long {
        if (slideSpeedAtTick(settings, elapsed, contactSpeedScale) <= MOVE_EPSILON) {
            return 0
        }
        var t = elapsed + 1
        while (t - elapsed <= 120) {
            if (slideSpeedAtTick(settings, t, contactSpeedScale) <= MOVE_EPSILON) {
                return t - elapsed
            }
            t++
        }
        return 120
    }

    fun shouldPlayLoop(
        elapsed: Long,
        endSoundPlayed: Boolean,
        settings: PlayerSlideTackleSettings,
        contactSpeedScale: Double,
    ): Boolean {
        if (endSoundPlayed) {
            return false
        }
        if (slideSpeedAtTick(settings, elapsed, contactSpeedScale) <= MOVE_EPSILON) {
            return false
        }
        if (elapsed < START_TICKS) {
            return false
        }
        return (elapsed - START_TICKS) % LOOP_INTERVAL_TICKS == 0L
    }

    fun shouldPlayEndEarly(
        settings: PlayerSlideTackleSettings,
        elapsed: Long,
        contactSpeedScale: Double,
    ): Boolean {
        val remaining = remainingSlideMovementTicks(settings, elapsed, contactSpeedScale)
        return remaining in 1..END_LEAD_TICKS
    }
}
