package net.astrorbits.football.input

import net.astrorbits.football.config.FootballConfigs
import net.astrorbits.football.config.FootballInputFlags

/**
 * @deprecated 请使用 [net.astrorbits.football.config.FootballConfigs] 与 [FootballInputFlags]。
 */
//@Deprecated("Use FootballConfigs", ReplaceWith("FootballConfigs"))
object FootballInputConfig {
    val PLAYER_KICK_RANGE get() = FootballConfigs.server.playerInput.playerKickRange
    val HINT_HIDE_EXTRA_RANGE get() = FootballConfigs.client.hintHideExtraRange
    val COMMAND_KICK_RANGE get() = FootballConfigs.server.playerInput.commandKickRange
    val PASS_FORCE get() = FootballConfigs.server.playerInput.passForce
    val SHOOT_FORCE_MIN get() = FootballConfigs.server.playerInput.shootForceMin
    val SHOOT_FORCE_MAX get() = FootballConfigs.server.playerInput.shootForceMax
    val SHOOT_SPRINT_BONUS get() = FootballConfigs.server.playerInput.shootSprintBonus
    val SHOOT_MIN_CHARGE_FOR_SPRINT get() = FootballConfigs.server.playerInput.shootMinChargeForSprint
    val CHIP_FORCE get() = FootballConfigs.server.playerInput.chipForce
    val CHIP_ANGLE_DEG get() = FootballConfigs.server.playerInput.chipAngleDeg
    val CHIP_ANGLE_EXTRA_MAX get() = FootballConfigs.server.playerInput.chipAngleExtraMax
    val CHIP_HEIGHT_OFFSET get() = FootballConfigs.server.playerInput.chipHeightOffset
    val DRIBBLE_TARGET_DISTANCE get() = FootballConfigs.server.playerInput.dribbleTargetDistance
    val DRIBBLE_MAX_CONTROL_RANGE get() = FootballConfigs.server.playerInput.dribbleMaxControlRange
    val DRIBBLE_SESSION_TIMEOUT_TICKS get() = FootballConfigs.server.playerInput.dribbleSessionTimeoutTicks
    val DRIBBLE_HOLD_PACKET_INTERVAL get() = FootballConfigs.client.dribbleHoldPacketInterval
    val DRIBBLE_SOUND_INTERVAL_TICKS get() = FootballConfigs.server.playerInput.dribbleSoundIntervalTicks
    val DRIBBLE_SOUND_INTERVAL_JITTER_TICKS get() = FootballConfigs.server.playerInput.dribbleSoundIntervalJitterTicks
    val DRIBBLE_POSITION_GAIN get() = FootballConfigs.server.playerInput.dribblePositionGain
    val DRIBBLE_VELOCITY_GAIN get() = FootballConfigs.server.playerInput.dribbleVelocityGain
    val DRIBBLE_MAX_CORRECTION get() = FootballConfigs.server.playerInput.dribbleMaxCorrection
    val DRIBBLE_SPEED_MATCH get() = FootballConfigs.server.playerInput.dribbleSpeedMatch
    val DRIBBLE_LATERAL_GAIN get() = FootballConfigs.server.playerInput.dribbleLateralGain
    val DRIBBLE_TOUCH_FORCE get() = FootballConfigs.server.playerInput.dribbleTouchForce
    val DRIBBLE_AIR_POSITION_SCALE get() = FootballConfigs.server.playerInput.dribbleAirPositionScale
    val TAP_MAX_MS get() = FootballConfigs.client.tapMaxMs
    val CHARGE_MIN_MS get() = FootballConfigs.client.chargeMinMs
    val CHARGE_MAX_MS get() = FootballConfigs.client.chargeMaxMs
    val ACTION_COOLDOWN_TICKS get() = FootballConfigs.server.playerInput.actionCooldownTicks
    val SHOOT_ANGLE_MIN_DEG get() = FootballConfigs.server.playerInput.shootAngleMinDeg
    val SHOOT_ANGLE_MAX_DEG get() = FootballConfigs.server.playerInput.shootAngleMaxDeg
    val KICK_LOOK_PITCH_INFLUENCE get() = FootballConfigs.server.playerInput.kickLookPitchInfluence
    val KICK_LOOK_PITCH_ANGLE_MIN get() = FootballConfigs.server.playerInput.kickLookPitchAngleMin
    val KICK_LOOK_PITCH_ANGLE_MAX get() = FootballConfigs.server.playerInput.kickLookPitchAngleMax
    const val FLAG_SPRINT = FootballInputFlags.SPRINT
    const val FLAG_DIVE_USE_LOOK = FootballInputFlags.DIVE_USE_LOOK
}
