package net.astrorbits.football.util

import kotlin.math.abs
import net.minecraft.world.phys.Vec3
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.physics.FootballPhysicsState

object CollisionUtil {
    fun resolveCollisions(
        state: FootballPhysicsState,
        horizontalCollision: Boolean,
        verticalCollisionBelow: Boolean,
        onGround: Boolean,
        intendedMotion: Vec3,
        actualMotion: Vec3
    ) {
        state.onGround = onGround || (verticalCollisionBelow && state.linearVelocity.y <= 0.0)

        if (state.onGround && state.linearVelocity.y < 0.0) {
            state.linearVelocity = Vec3(
                state.linearVelocity.x,
                -state.linearVelocity.y * FootballPhysicsConfig.RESTITUTION,
                state.linearVelocity.z
            )
        }

        if (horizontalCollision) {
            resolveHorizontalWall(state, intendedMotion, actualMotion)
        }

        if (state.onGround) {
            state.linearVelocity = Vec3(
                state.linearVelocity.x * FootballPhysicsConfig.GROUND_FRICTION,
                state.linearVelocity.y,
                state.linearVelocity.z * FootballPhysicsConfig.GROUND_FRICTION
            )
            state.angularVelocity = Vec3(
                state.angularVelocity.x * FootballPhysicsConfig.GROUND_SPIN_FRICTION,
                state.angularVelocity.y * FootballPhysicsConfig.GROUND_SPIN_FRICTION,
                state.angularVelocity.z * FootballPhysicsConfig.GROUND_SPIN_FRICTION
            )

            val stuckAgainstWall = horizontalCollision &&
                Vec3Math.horizontal(actualMotion).lengthSqr() < FootballPhysicsConfig.STOP_SPEED_SQR
            if (!stuckAgainstWall) {
                applyRollingCoupling(state)
            } else {
                state.angularVelocity = state.angularVelocity.scale(FootballPhysicsConfig.STUCK_SPIN_DRAG)
            }
        }

        applyStopThreshold(state)
    }

    private fun resolveHorizontalWall(
        state: FootballPhysicsState,
        intended: Vec3,
        actual: Vec3
    ) {
        var vx = state.linearVelocity.x
        var vz = state.linearVelocity.z
        val eps = FootballPhysicsConfig.EPSILON

        if (abs(intended.x) > eps && abs(actual.x) < eps) {
            vx = 0.0
        } else if (abs(intended.x) > eps) {
            vx *= FootballPhysicsConfig.WALL_RESTITUTION
        }

        if (abs(intended.z) > eps && abs(actual.z) < eps) {
            vz = 0.0
        } else if (abs(intended.z) > eps) {
            vz *= FootballPhysicsConfig.WALL_RESTITUTION
        }

        state.linearVelocity = Vec3(vx, state.linearVelocity.y, vz)
    }

    /**
     * 双向滚动耦合：线速度与绕 Y 轴无关的水平自转相互逼近无滑滚动关系，避免单方向耦合从 ω 持续泵入动能。
     */
    private fun applyRollingCoupling(state: FootballPhysicsState) {
        val radius = FootballPhysicsConfig.RADIUS
        val coupling = FootballPhysicsConfig.ROLL_COUPLING

        val targetVx = -radius * state.angularVelocity.z
        val targetVz = radius * state.angularVelocity.x
        val newVx = state.linearVelocity.x + (targetVx - state.linearVelocity.x) * coupling
        val newVz = state.linearVelocity.z + (targetVz - state.linearVelocity.z) * coupling

        val targetOmegaX = newVz / radius
        val targetOmegaZ = -newVx / radius
        val newOmegaX = state.angularVelocity.x + (targetOmegaX - state.angularVelocity.x) * coupling
        val newOmegaZ = state.angularVelocity.z + (targetOmegaZ - state.angularVelocity.z) * coupling

        state.linearVelocity = Vec3(newVx, state.linearVelocity.y, newVz)
        state.angularVelocity = Vec3(newOmegaX, state.angularVelocity.y, newOmegaZ)
    }

    private fun applyStopThreshold(state: FootballPhysicsState) {
        if (!state.onGround) {
            return
        }

        val horizontalSpeedSqr = Vec3Math.horizontal(state.linearVelocity).lengthSqr()
        if (horizontalSpeedSqr >= FootballPhysicsConfig.STOP_SPEED_SQR) {
            return
        }

        state.linearVelocity = Vec3(0.0, state.linearVelocity.y, 0.0)
        state.angularVelocity = Vec3(0.0, state.angularVelocity.y, 0.0)

        if (abs(state.linearVelocity.y) < FootballPhysicsConfig.EPSILON) {
            state.linearVelocity = Vec3.ZERO
        }
    }
}
