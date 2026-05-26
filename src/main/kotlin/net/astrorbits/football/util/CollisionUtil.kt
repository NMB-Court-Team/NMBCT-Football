package net.astrorbits.football.util

import net.minecraft.world.phys.Vec3
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.physics.FootballPhysicsState

object CollisionUtil {
    fun resolveCollisions(
        state: FootballPhysicsState,
        horizontalCollision: Boolean,
        verticalCollisionBelow: Boolean,
        onGround: Boolean
    ) {
        state.onGround = onGround || (verticalCollisionBelow && state.linearVelocity.y <= 0.0)

        if (state.onGround && state.linearVelocity.y < 0.0) {
            state.linearVelocity = Vec3(
                state.linearVelocity.x,
                -state.linearVelocity.y * FootballPhysicsConfig.RESTITUTION,
                state.linearVelocity.z
            )
        }

        if (state.onGround) {
            state.linearVelocity = Vec3(
                state.linearVelocity.x * FootballPhysicsConfig.GROUND_FRICTION,
                state.linearVelocity.y,
                state.linearVelocity.z * FootballPhysicsConfig.GROUND_FRICTION
            )
            applyRollingCoupling(state)
        }

        if (horizontalCollision) {
            state.linearVelocity = Vec3(
                state.linearVelocity.x * FootballPhysicsConfig.WALL_RESTITUTION,
                state.linearVelocity.y,
                state.linearVelocity.z * FootballPhysicsConfig.WALL_RESTITUTION
            )
        }
    }

    private fun applyRollingCoupling(state: FootballPhysicsState) {
        val radius = FootballPhysicsConfig.RADIUS
        val targetVx = -radius * state.angularVelocity.z
        val targetVz = radius * state.angularVelocity.x
        val coupling = FootballPhysicsConfig.ROLL_COUPLING
        state.linearVelocity = Vec3(
            state.linearVelocity.x + (targetVx - state.linearVelocity.x) * coupling,
            state.linearVelocity.y,
            state.linearVelocity.z + (targetVz - state.linearVelocity.z) * coupling
        )
    }
}
