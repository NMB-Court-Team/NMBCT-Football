package net.astrorbits.football.util

import net.minecraft.world.phys.Vec3
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.physics.CollisionBounceResult
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.physics.FootballPhysicsState
import org.joml.Quaternionf

object FootballPhysicsSimulator {
    fun applyKick(
        state: FootballPhysicsState,
        kickPoint: Vec3,
        direction: Vec3,
        center: Vec3
    ) {
        val impulse = direction.scale(FootballPhysicsConfig.KICK_FORCE_SCALE)
        state.linearVelocity = state.linearVelocity.add(impulse.scale(1.0 / FootballPhysicsConfig.MASS))
        redirectHorizontalVelocityTowardKick(state, direction)

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

        val along = kickDir.scale(horizontal.dot(kickDir))
        val perpendicular = horizontal.subtract(along)
            .scale(FootballPhysicsConfig.KICK_MOVING_LATERAL_DAMP)
        val redirected = along.add(perpendicular)
        state.linearVelocity = Vec3(redirected.x, state.linearVelocity.y, redirected.z)
    }

    /**
     * 身体触球（走路/跑步/滑铲）：将球员水平速度叠加到球上，并同步无滑滚动角速度与朝向。
     *
     * @param velocityTransfer 速度传递比例（滑铲一般为 1.0）
     */
    fun applyPlayerBodyBallPush(
        state: FootballPhysicsState,
        playerHorizontalVelocity: Vec3,
        velocityTransfer: Double = 1.0,
    ): Boolean {
        val player = Vec3Math.horizontal(playerHorizontalVelocity)
        if (player.lengthSqr() < FootballPhysicsConfig.EPSILON * FootballPhysicsConfig.EPSILON) {
            return false
        }
        if (player.lengthSqr() < FootballInputConfig.PLAYER_BALL_PUSH_MIN_SPEED * FootballInputConfig.PLAYER_BALL_PUSH_MIN_SPEED) {
            return false
        }

        val transfer = velocityTransfer.coerceIn(0.0, 1.25)
        val ballHorizontal = Vec3Math.horizontal(state.linearVelocity)
        val merged = ballHorizontal.add(player.scale(transfer))
        state.linearVelocity = Vec3(merged.x, state.linearVelocity.y, merged.z)
        resetRollingOrientation(state)
        val rolling = Vec3Math.rollingAngularVelocity(
            Vec3Math.horizontal(state.linearVelocity),
            FootballPhysicsConfig.RADIUS,
        )
        state.angularVelocity = Vec3(rolling.x, state.angularVelocity.y, rolling.z)
        return true
    }

    /** 将接触法线翻转为沿「球员脚点 → 球心」方向，保证推球离开球员。 */
    fun orientContactNormalTowardBall(contactNormal: Vec3, playerFeetPosition: Vec3, ballCenter: Vec3): Vec3 {
        val towardBall = Vec3Math.horizontal(ballCenter.subtract(playerFeetPosition))
        val fallback = Vec3Math.normalizeSafe(towardBall)
        val raw = Vec3Math.normalizeSafe(Vec3Math.horizontal(contactNormal), fallback)
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
