package net.astrorbits.football.physics

import net.astrorbits.football.config.FootballConfigs

/**
 * @deprecated 请使用 [net.astrorbits.football.config.FootballConfigs.server.physics]。
 */
@Deprecated("Use FootballConfigs.server.physics", ReplaceWith("FootballConfigs.server.physics"))
object FootballPhysicsConfig {
    val RADIUS get() = FootballConfigs.server.physics.radius
    val MASS get() = FootballConfigs.server.physics.mass
    val INERTIA get() = FootballConfigs.server.physics.inertia
    val RESTITUTION get() = FootballConfigs.server.physics.restitution
    val WALL_RESTITUTION get() = FootballConfigs.server.physics.wallRestitution
    val WALL_SPIN_RETENTION get() = FootballConfigs.server.physics.wallSpinRetention
    val WALL_BOUNCE_COOLDOWN_TICKS get() = FootballConfigs.server.physics.wallBounceCooldownTicks
    val WALL_BLOCK_RATIO get() = FootballConfigs.server.physics.wallBlockRatio
    val WALL_YAW_SPIN_DAMP get() = FootballConfigs.server.physics.wallYawSpinDamp
    val GROUND_FRICTION get() = FootballConfigs.server.physics.groundFriction
    val GROUND_SPIN_FRICTION get() = FootballConfigs.server.physics.groundSpinFriction
    val STUCK_SPIN_DRAG get() = FootballConfigs.server.physics.stuckSpinDrag
    val STOP_SPEED_SQR get() = FootballConfigs.server.physics.stopSpeedSqr
    val GROUND_SETTLE_VY get() = FootballConfigs.server.physics.groundSettleVy
    val GROUND_YAW_SPIN_FRICTION get() = FootballConfigs.server.physics.groundYawSpinFriction
    val AIR_DRAG get() = FootballConfigs.server.physics.airDrag
    val SPIN_DRAG get() = FootballConfigs.server.physics.spinDrag
    val GRAVITY get() = FootballConfigs.server.physics.gravity
    val ROLL_COUPLING get() = FootballConfigs.server.physics.rollCoupling
    val COBWEB_HORIZONTAL_DRAG get() = FootballConfigs.server.physics.cobwebHorizontalDrag
    val COBWEB_VERTICAL_DRAG get() = FootballConfigs.server.physics.cobwebVerticalDrag
    val COBWEB_SPIN_DRAG get() = FootballConfigs.server.physics.cobwebSpinDrag
    val BOUNCE_SOUND_MIN_WALL_SPEED get() = FootballConfigs.server.physics.bounceSoundMinWallSpeed
    val BOUNCE_SOUND_MIN_GROUND_VY get() = FootballConfigs.server.physics.bounceSoundMinGroundVy
    val EPSILON get() = FootballConfigs.server.physics.epsilon
    val KICK_FORCE_SCALE get() = FootballConfigs.server.physics.kickForceScale
    val KICK_MOVING_LATERAL_DAMP get() = FootballConfigs.server.physics.kickMovingLateralDamp
    val ORIENTATION_RESET_VELOCITY_DELTA get() = FootballConfigs.server.physics.orientationResetVelocityDelta
    val ORIENTATION_RESET_OMEGA_DELTA get() = FootballConfigs.server.physics.orientationResetOmegaDelta
    val RENDER_STATIONARY_SPEED_SQR get() = FootballConfigs.client.renderStationarySpeedSqr
    val CLIENT_CORRECTION_THRESHOLD get() = FootballConfigs.client.clientCorrectionThreshold
}
