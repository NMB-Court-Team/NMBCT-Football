package net.astrorbits.football.physics

object FootballPhysicsConfig {
    const val RADIUS = 0.25
    const val MASS = 0.45
    const val INERTIA = 2.0 / 5.0 * MASS * RADIUS * RADIUS

    const val RESTITUTION = 0.68
    const val WALL_RESTITUTION = 0.55
    const val GROUND_FRICTION = 0.92
    const val AIR_DRAG = 0.99
    const val SPIN_DRAG = 0.995
    const val GRAVITY = 0.04
    const val ROLL_COUPLING = 0.15

    const val EPSILON = 1.0e-4
    const val CLIENT_CORRECTION_THRESHOLD = 0.5
}
