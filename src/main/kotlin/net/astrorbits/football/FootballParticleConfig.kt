package net.astrorbits.football

import net.astrorbits.football.config.FootballConfigs

/**
 * @deprecated 请使用 [net.astrorbits.football.config.FootballConfigs.server.particles]。
 */
@Deprecated("Use FootballConfigs.server.particles", ReplaceWith("FootballConfigs.server.particles"))
object FootballParticleConfig {
    val BOUNCE_PARTICLE_MIN_GROUND_VY get() = FootballConfigs.server.particles.bounceParticleMinGroundVy
    val BOUNCE_PARTICLE_MIN_WALL_SPEED get() = FootballConfigs.server.particles.bounceParticleMinWallSpeed
    val BOUNCE_PARTICLE_REFERENCE_SPEED get() = FootballConfigs.server.particles.bounceParticleReferenceSpeed
    val KICK_COUNT_BASE get() = FootballConfigs.server.particles.kickCountBase
    val KICK_COUNT_FORCE_EXTRA get() = FootballConfigs.server.particles.kickCountForceExtra
    val DRIBBLE_COUNT get() = FootballConfigs.server.particles.dribbleCount
    val TRAP_COUNT get() = FootballConfigs.server.particles.trapCount
    val PLACE_COUNT get() = FootballConfigs.server.particles.placeCount
    val GK_CATCH_COUNT get() = FootballConfigs.server.particles.gkCatchCount
    val GK_DIVE_COUNT get() = FootballConfigs.server.particles.gkDiveCount
    val GK_PUNCH_COUNT get() = FootballConfigs.server.particles.gkPunchCount
    val GK_THROW_COUNT get() = FootballConfigs.server.particles.gkThrowCount
    val SPREAD_HORIZONTAL get() = FootballConfigs.server.particles.spreadHorizontal
    val SPREAD_VERTICAL get() = FootballConfigs.server.particles.spreadVertical
    val KICK_PARTICLE_SPEED get() = FootballConfigs.server.particles.kickParticleSpeed
    val BOUNCE_PARTICLE_SPEED get() = FootballConfigs.server.particles.bounceParticleSpeed
    val GK_DIVE_PARTICLE_SPEED get() = FootballConfigs.server.particles.gkDiveParticleSpeed
    val HIGH_SPEED_DRAG_MIN_SPEED get() = FootballConfigs.server.particles.highSpeedDragMinSpeed
    val HIGH_SPEED_DRAG_COUNT_BASE get() = FootballConfigs.server.particles.highSpeedDragCountBase
    val HIGH_SPEED_DRAG_COUNT_EXTRA get() = FootballConfigs.server.particles.highSpeedDragCountExtra
    val HIGH_SPEED_DRAG_REFERENCE_SPEED get() = FootballConfigs.server.particles.highSpeedDragReferenceSpeed
    val HIGH_SPEED_DRAG_RING_RADIUS get() = FootballConfigs.server.particles.highSpeedDragRingRadius
    val HIGH_SPEED_DRAG_TRAIL_FORWARD_DISTANCE get() = FootballConfigs.server.particles.highSpeedDragTrailForwardDistance
    val HIGH_SPEED_DRAG_VERTICAL_OFFSET get() = FootballConfigs.server.particles.highSpeedDragVerticalOffset
    val HIGH_SPEED_DRAG_TRAIL_DURATION_TICKS get() = FootballConfigs.server.particles.highSpeedDragTrailDurationTicks
    val HIGH_SPEED_DRAG_TRAIL_COLOR_LOW_RGB get() = FootballConfigs.server.particles.highSpeedDragTrailColorLowRgb
    val HIGH_SPEED_DRAG_TRAIL_COLOR_HIGH_RGB get() = FootballConfigs.server.particles.highSpeedDragTrailColorHighRgb
    val HIGH_SPEED_DRAG_COLOR_RED_START get() = FootballConfigs.server.particles.highSpeedDragColorRedStart
    val KICK_SWEEP_FOOT_FORWARD get() = FootballConfigs.server.particles.kickSweepFootForward
    val KICK_SWEEP_FOOT_HEIGHT get() = FootballConfigs.server.particles.kickSweepFootHeight
    val KICK_CLOUD_RING_BASE_COUNT get() = FootballConfigs.server.particles.kickCloudRingBaseCount
    val KICK_CLOUD_RING_FORCE_EXTRA get() = FootballConfigs.server.particles.kickCloudRingForceExtra
    val KICK_CLOUD_RING_INNER_RADIUS get() = FootballConfigs.server.particles.kickCloudRingInnerRadius
    val KICK_CLOUD_RING_OUTER_RADIUS get() = FootballConfigs.server.particles.kickCloudRingOuterRadius
    val KICK_CLOUD_RING_RADIAL_SPEED get() = FootballConfigs.server.particles.kickCloudRingRadialSpeed
}
