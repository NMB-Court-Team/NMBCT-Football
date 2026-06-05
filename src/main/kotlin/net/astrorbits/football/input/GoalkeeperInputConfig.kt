package net.astrorbits.football.input

import net.astrorbits.football.config.FootballConfigs

/**
 * @deprecated ????[net.astrorbits.football.config.FootballConfigs.server.goalkeeper]?? */
//@Deprecated("Use FootballConfigs.server.goalkeeper", ReplaceWith("FootballConfigs.server.goalkeeper"))
object GoalkeeperInputConfig {
    val GK_CATCH_RANGE get() = FootballConfigs.server.goalkeeper.catchRange
    val GK_CROUCH_RANGE_BONUS get() = FootballConfigs.server.goalkeeper.crouchRangeBonus
    val GK_CATCH_MAX_SPEED get() = FootballConfigs.server.goalkeeper.catchMaxSpeed
    val GK_CATCH_ANGLE_DEG get() = FootballConfigs.server.goalkeeper.catchAngleDeg
    val GK_HOLD_HEIGHT get() = FootballConfigs.server.goalkeeper.holdHeight
    val GK_HOLD_FORWARD get() = FootballConfigs.server.goalkeeper.holdForward
    val GK_HOLD_CROUCH_HEIGHT_OFFSET get() = FootballConfigs.server.goalkeeper.holdCrouchHeightOffset
    val GK_HOLD_FIRST_PERSON_EXTRA_FORWARD get() = FootballConfigs.server.goalkeeper.holdFirstPersonExtraForward
    val GK_HOLD_FIRST_PERSON_EXTRA_DOWN get() = FootballConfigs.server.goalkeeper.holdFirstPersonExtraDown
    val GK_DROP_DISTANCE get() = FootballConfigs.server.goalkeeper.dropDistance
    val GK_DIVE_DURATION_TICKS get() = FootballConfigs.server.goalkeeper.diveDurationTicks
    val GK_DIVE_COOLDOWN_TICKS get() = FootballConfigs.server.goalkeeper.diveCooldownTicks
    val GK_DIVE_RANGE get() = FootballConfigs.server.goalkeeper.diveRange
    val GK_DIVE_HALF_ANGLE_DEG get() = FootballConfigs.server.goalkeeper.diveHalfAngleDeg
    val GK_DIVE_SPEED get() = FootballConfigs.server.goalkeeper.diveSpeed
    val GK_DIVE_CATCH_MAX_SPEED get() = FootballConfigs.server.goalkeeper.diveCatchMaxSpeed
    val GK_DIVE_DEFLECT_FORCE_SCALE get() = FootballConfigs.server.goalkeeper.diveDeflectForceScale
    val GK_DIVE_CATCH_RECOIL_MIN_SPEED get() = FootballConfigs.server.goalkeeper.diveCatchRecoilMinSpeed
    val GK_DIVE_CATCH_ORIGIN_EYE_SCALE get() = FootballConfigs.server.goalkeeper.diveCatchOriginEyeScale
    val GK_DIVE_CLOSE_RANGE get() = FootballConfigs.server.goalkeeper.diveCloseRange
    val GK_DIVE_HIGH_BALL_MIN_HEIGHT get() = FootballConfigs.server.goalkeeper.diveHighBallMinHeight
    val GK_DIVE_HIGH_BALL_EXTRA_HALF_ANGLE_DEG get() = FootballConfigs.server.goalkeeper.diveHighBallExtraHalfAngleDeg
    val GK_DIVE_CLOSE_VERTICAL_BELOW_FEET get() = FootballConfigs.server.goalkeeper.diveCloseVerticalBelowFeet
    val GK_DIVE_CLOSE_VERTICAL_ABOVE_HEAD get() = FootballConfigs.server.goalkeeper.diveCloseVerticalAboveHead
    val GK_DIVE_PITCH get() = FootballConfigs.server.goalkeeper.divePitch
    val GK_DIVE_IMPULSE get() = FootballConfigs.server.goalkeeper.diveImpulse
    val GK_PUNCH_RANGE get() = FootballConfigs.server.goalkeeper.punchRange
    val GK_PUNCH_FORCE get() = FootballConfigs.server.goalkeeper.punchForce
    val GK_THROW_SHORT_FORCE get() = FootballConfigs.server.goalkeeper.throwShortForce
    val GK_THROW_LONG_FORCE_MIN get() = FootballConfigs.server.goalkeeper.throwLongForceMin
    val GK_THROW_LONG_FORCE_MAX get() = FootballConfigs.server.goalkeeper.throwLongForceMax
    val GK_THROW_LONG_ANGLE_MIN_DEG get() = FootballConfigs.server.goalkeeper.throwLongAngleMinDeg
    val GK_THROW_LONG_ANGLE_MAX_DEG get() = FootballConfigs.server.goalkeeper.throwLongAngleMaxDeg
    val GK_THROW_SPRINT_BONUS get() = FootballConfigs.server.goalkeeper.throwSprintBonus
    val GK_ACTION_COOLDOWN_TICKS get() = FootballConfigs.server.goalkeeper.actionCooldownTicks
    val GK_HOLD_RELEASE_LOCK_TICKS get() = FootballConfigs.server.goalkeeper.holdReleaseLockTicks
    val GK_HOLD_STEAL_PROTECTION_TICKS get() = FootballConfigs.server.goalkeeper.holdStealProtectionTicks
}