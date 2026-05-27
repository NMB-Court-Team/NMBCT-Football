package net.astrorbits.football.input

object FootballInputConfig {
    const val PLAYER_KICK_RANGE = 2.5
    const val COMMAND_KICK_RANGE = 3.0

    const val PASS_FORCE = 1.5
    const val SHOOT_FORCE_MIN = 2.0
    const val SHOOT_FORCE_MAX = 4.0
    const val SHOOT_SPRINT_BONUS = 1.15
    const val SHOOT_MIN_CHARGE_FOR_SPRINT = 0.6f

    const val CHIP_FORCE = 1.4
    const val CHIP_ANGLE_DEG = 42.0
    const val CHIP_ANGLE_EXTRA_MAX = 13.0
    const val CHIP_HEIGHT_OFFSET = -0.15

    const val DRIBBLE_FORCE = 0.7
    const val DRIBBLE_INTERVAL_TICKS = 4

    const val TAP_MAX_MS = 250L
    const val CHARGE_MIN_MS = 300L
    const val CHARGE_MAX_MS = 1200L

    const val ACTION_COOLDOWN_TICKS = 3

    const val SHOOT_ANGLE_MIN_DEG = 5.0
    const val SHOOT_ANGLE_MAX_DEG = 18.0

    const val FLAG_SPRINT = 1
}
