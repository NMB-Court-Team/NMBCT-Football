package net.astrorbits.football.util

import net.minecraft.world.phys.Vec3
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.physics.FootballPhysicsState

object FootballPhysicsSimulator {
    fun applyKick(
        state: FootballPhysicsState,
        kickPoint: Vec3,
        direction: Vec3,
        center: Vec3
    ) {
        val impulse = direction
        state.linearVelocity = state.linearVelocity.add(impulse.scale(1.0 / FootballPhysicsConfig.MASS))
        val leverArm = kickPoint.subtract(center)
        val angularImpulse = leverArm.cross(impulse)
        state.angularVelocity = state.angularVelocity.add(angularImpulse.scale(1.0 / FootballPhysicsConfig.INERTIA))
    }

    fun applyAirForces(state: FootballPhysicsState) {
        state.linearVelocity = state.linearVelocity.add(0.0, -FootballPhysicsConfig.GRAVITY, 0.0)
        state.linearVelocity = state.linearVelocity.scale(FootballPhysicsConfig.AIR_DRAG)
        state.angularVelocity = state.angularVelocity.scale(FootballPhysicsConfig.SPIN_DRAG)
    }

    fun resolveCollisions(
        state: FootballPhysicsState,
        horizontalCollision: Boolean,
        verticalCollisionBelow: Boolean,
        onGround: Boolean
    ) {
        CollisionUtil.resolveCollisions(state, horizontalCollision, verticalCollisionBelow, onGround)
    }

    fun integrateOrientation(state: FootballPhysicsState) {
        state.orientation = QuaternionMath.integrate(state.orientation, state.angularVelocity)
    }

    fun getRollingDirection(state: FootballPhysicsState): Vec3 {
        val horizontalVelocity = Vec3Math.horizontal(state.linearVelocity)
        if (horizontalVelocity.lengthSqr() > FootballPhysicsConfig.EPSILON * FootballPhysicsConfig.EPSILON) {
            return Vec3Math.normalizeSafe(horizontalVelocity)
        }

        if (state.onGround) {
            val fromSpin = Vec3(
                -FootballPhysicsConfig.RADIUS * state.angularVelocity.z,
                0.0,
                FootballPhysicsConfig.RADIUS * state.angularVelocity.x
            )
            if (fromSpin.lengthSqr() > FootballPhysicsConfig.EPSILON * FootballPhysicsConfig.EPSILON) {
                return Vec3Math.normalizeSafe(fromSpin)
            }

            val spinRoll = Vec3Math.horizontal(Vec3Math.cross(Vec3(0.0, 1.0, 0.0), state.angularVelocity))
            if (spinRoll.lengthSqr() > FootballPhysicsConfig.EPSILON * FootballPhysicsConfig.EPSILON) {
                return Vec3Math.normalizeSafe(spinRoll)
            }
        }

        if (horizontalVelocity.lengthSqr() > FootballPhysicsConfig.EPSILON * FootballPhysicsConfig.EPSILON) {
            return Vec3Math.normalizeSafe(horizontalVelocity)
        }

        return Vec3.ZERO
    }
}
