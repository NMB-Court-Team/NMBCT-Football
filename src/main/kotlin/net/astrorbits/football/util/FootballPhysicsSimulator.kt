package net.astrorbits.football.util

import net.astrorbits.football.physics.CollisionBounceResult
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.physics.FootballPhysicsState
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

object FootballPhysicsSimulator {
    /** 低于此水平速度时全额侧向衰减；高于 [LATERAL_DAMP_SPEED_FULL] 时保留全部侧向动量。 */
    private const val LATERAL_DAMP_SPEED_MIN = 0.35
    private const val LATERAL_DAMP_SPEED_FULL = 1.2

    fun applyKick(
        state: FootballPhysicsState,
        kickPoint: Vec3,
        direction: Vec3,
        center: Vec3,
        redirectMovingLateral: Boolean = true,
    ) {
        val impulse = direction.scale(FootballPhysicsConfig.KICK_FORCE_SCALE)
        state.linearVelocity = state.linearVelocity.add(impulse.scale(1.0 / FootballPhysicsConfig.MASS))
        if (redirectMovingLateral) {
            redirectHorizontalVelocityTowardKick(state, direction)
        }

        val leverArm = kickPoint.subtract(center)
        val eccentricTorque = leverArm.cross(impulse).scale(1.0 / FootballPhysicsConfig.INERTIA)

        val rolling = Vec3Math.rollingAngularVelocity(
            Vec3Math.horizontal(state.linearVelocity),
            FootballPhysicsConfig.RADIUS
        )

        state.angularVelocity = Vec3(
            rolling.x + eccentricTorque.x,
            0.0,
            rolling.z + eccentricTorque.z
        )
        resetRollingOrientation(state)
    }

    /** 踢球或速度突变后重置姿态，使四元数与新的滚动角速度一致（避免沿用旧朝向积分）。 */
    fun resetRollingOrientation(state: FootballPhysicsState) {
        state.orientation = Quaternionf().identity()
    }

    /**
     * 再踢滚动中的球时，削弱与踢球方向垂直的旧速度分量，使转向跟脚。
     */
    private fun redirectHorizontalVelocityTowardKick(state: FootballPhysicsState, direction: Vec3) {
        val kickDir = Vec3Math.normalizeSafe(Vec3Math.horizontal(direction))
        if (kickDir.lengthSqr() < FootballPhysicsConfig.EPSILON * FootballPhysicsConfig.EPSILON) {
            return
        }

        val horizontal = Vec3Math.horizontal(state.linearVelocity)
        if (horizontal.lengthSqr() < FootballPhysicsConfig.EPSILON * FootballPhysicsConfig.EPSILON) {
            return
        }

        val speed = horizontal.length()
        val baseDamp = FootballPhysicsConfig.KICK_MOVING_LATERAL_DAMP
        val effectiveDamp = lateralDampForSpeed(speed, baseDamp)

        val along = kickDir.scale(horizontal.dot(kickDir))
        val perpendicular = horizontal.subtract(along).scale(effectiveDamp)
        val redirected = along.add(perpendicular)
        state.linearVelocity = Vec3(redirected.x, state.linearVelocity.y, redirected.z)
    }

    private fun lateralDampForSpeed(speed: Double, baseDamp: Double): Double {
        val blend = ((speed - LATERAL_DAMP_SPEED_MIN) / (LATERAL_DAMP_SPEED_FULL - LATERAL_DAMP_SPEED_MIN))
            .coerceIn(0.0, 1.0)
        return baseDamp + (1.0 - baseDamp) * blend
    }

    /** 确保接触法线（玩家 → 球）与球心相对脚点方向同向；保留 3D 分量以反映撞击高度。 */
    fun orientContactNormalTowardBall(contactNormal: Vec3, playerFeetPosition: Vec3, ballCenter: Vec3): Vec3 {
        val towardBall = ballCenter.subtract(playerFeetPosition)
        val fallback = Vec3Math.normalizeSafe(towardBall)
        val raw = Vec3Math.normalizeSafe(contactNormal, fallback)
        if (towardBall.lengthSqr() < FootballPhysicsConfig.EPSILON * FootballPhysicsConfig.EPSILON) {
            return raw
        }
        return if (raw.dot(towardBall) >= 0.0) raw else raw.scale(-1.0)
    }

    fun applyAirForces(state: FootballPhysicsState) {
        if (!state.onGround || state.linearVelocity.y > 0.0) {
            state.linearVelocity = state.linearVelocity.add(0.0, -FootballPhysicsConfig.GRAVITY, 0.0)
        }
        state.linearVelocity = state.linearVelocity.scale(FootballPhysicsConfig.AIR_DRAG)
        state.angularVelocity = state.angularVelocity.scale(FootballPhysicsConfig.SPIN_DRAG)
    }

    fun resolveCollisions(
        state: FootballPhysicsState,
        horizontalCollision: Boolean,
        verticalCollisionBelow: Boolean,
        onGround: Boolean,
        intendedMotion: Vec3,
        actualMotion: Vec3
    ): CollisionBounceResult = CollisionUtil.resolveCollisions(
        state,
        horizontalCollision,
        verticalCollisionBelow,
        onGround,
        intendedMotion,
        actualMotion
    )

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
