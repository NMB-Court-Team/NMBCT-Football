package net.astrorbits.football.util

import kotlin.math.abs
import net.minecraft.world.phys.Vec3
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.physics.FootballPhysicsState
import net.astrorbits.football.physics.CollisionBounceResult

object CollisionUtil {
    fun resolveCollisions(
        state: FootballPhysicsState,
        horizontalCollision: Boolean,
        verticalCollisionBelow: Boolean,
        onGround: Boolean,
        intendedMotion: Vec3,
        actualMotion: Vec3
    ): CollisionBounceResult {
        state.onGround = onGround || (verticalCollisionBelow && state.linearVelocity.y <= 0.0)

        if (state.wallBounceCooldown > 0) {
            state.wallBounceCooldown--
        }

        var groundImpactSpeed = 0.0
        if (state.onGround && state.linearVelocity.y < 0.0) {
            val vy = state.linearVelocity.y
            if (abs(vy) >= FootballPhysicsConfig.GROUND_SETTLE_VY) {
                groundImpactSpeed = -vy
            }
            val settledVy = if (abs(vy) < FootballPhysicsConfig.GROUND_SETTLE_VY) {
                0.0
            } else {
                -vy * FootballPhysicsConfig.RESTITUTION
            }
            state.linearVelocity = Vec3(
                state.linearVelocity.x,
                settledVy,
                state.linearVelocity.z
            )
        }

        var wallImpactSpeed = 0.0
        var wallBounced = false
        if (horizontalCollision) {
            val wallResult = resolveHorizontalWall(state, intendedMotion, actualMotion)
            wallBounced = wallResult.first
            wallImpactSpeed = wallResult.second
            if (wallBounced) {
                state.wallBounceCooldown = FootballPhysicsConfig.WALL_BOUNCE_COOLDOWN_TICKS
            }
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
            when {
                wallBounced -> applyWallBounceSpin(state)
                state.wallBounceCooldown > 0 -> Unit
                !stuckAgainstWall -> applyRollingCoupling(state)
                else -> state.angularVelocity = state.angularVelocity.scale(
                    FootballPhysicsConfig.STUCK_SPIN_DRAG
                )
            }
        } else if (wallBounced) {
            applyWallBounceSpin(state)
        }

        applyStopThreshold(state)
        return CollisionBounceResult(
            groundImpactSpeed = groundImpactSpeed,
            wallImpactSpeed = wallImpactSpeed,
        )
    }

    /**
     * 根据「意图位移 − 实际位移」估计墙法线并反射水平速度。
     * 比逐轴判断 actual ≈ 0 更可靠，高速撞墙时 MC 的 actual 位移往往并非精确为零。
     */
    private fun resolveHorizontalWall(
        state: FootballPhysicsState,
        intended: Vec3,
        actual: Vec3
    ): Pair<Boolean, Double> {
        val blocked = Vec3Math.horizontal(intended.subtract(actual))
        if (blocked.lengthSqr() < FootballPhysicsConfig.EPSILON * FootballPhysicsConfig.EPSILON) {
            return resolveHorizontalWallByAxis(state, intended, actual)
        }

        val normal = Vec3Math.normalizeSafe(blocked)
        val horizontalVelocity = Vec3Math.horizontal(state.linearVelocity)
        val decomp = Vec3Math.decomposePlanar(horizontalVelocity, normal)
        if (decomp.normalComponent <= FootballPhysicsConfig.EPSILON) {
            return false to 0.0
        }

        val reflected = horizontalVelocity.subtract(
            normal.scale(decomp.normalComponent * (1.0 + FootballPhysicsConfig.WALL_RESTITUTION))
        )
        state.linearVelocity = Vec3(reflected.x, state.linearVelocity.y, reflected.z)
        return true to decomp.normalComponent
    }

    /** 向量法线不可靠时的逐轴兜底（使用位移完成比例而非 actual ≈ 0）。 */
    private fun resolveHorizontalWallByAxis(
        state: FootballPhysicsState,
        intended: Vec3,
        actual: Vec3
    ): Pair<Boolean, Double> {
        var vx = state.linearVelocity.x
        var vz = state.linearVelocity.z
        val eps = FootballPhysicsConfig.EPSILON
        val ratioLimit = FootballPhysicsConfig.WALL_BLOCK_RATIO
        var bounced = false
        var impactSpeed = 0.0

        if (abs(intended.x) > eps) {
            val completion = abs(actual.x / intended.x)
            if (completion < ratioLimit && vx * intended.x > 0.0) {
                impactSpeed = maxOf(impactSpeed, abs(vx))
                vx = -vx * FootballPhysicsConfig.WALL_RESTITUTION
                bounced = true
            }
        }

        if (abs(intended.z) > eps) {
            val completion = abs(actual.z / intended.z)
            if (completion < ratioLimit && vz * intended.z > 0.0) {
                impactSpeed = maxOf(impactSpeed, abs(vz))
                vz = -vz * FootballPhysicsConfig.WALL_RESTITUTION
                bounced = true
            }
        }

        if (!bounced) {
            return false to 0.0
        }

        state.linearVelocity = Vec3(vx, state.linearVelocity.y, vz)
        return true to impactSpeed
    }

    /**
     * 撞墙后仅保留少量与新线速度同向的自转，避免无滑滚动关系把球立刻拉回墙面。
     */
    private fun applyWallBounceSpin(state: FootballPhysicsState) {
        val rolling = Vec3Math.rollingAngularVelocity(
            Vec3Math.horizontal(state.linearVelocity),
            FootballPhysicsConfig.RADIUS
        )
        val spinRetention = FootballPhysicsConfig.WALL_SPIN_RETENTION
        state.angularVelocity = Vec3(
            rolling.x * spinRetention,
            state.angularVelocity.y * FootballPhysicsConfig.WALL_YAW_SPIN_DAMP,
            rolling.z * spinRetention
        )
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
        val newOmegaY = state.angularVelocity.y * FootballPhysicsConfig.GROUND_YAW_SPIN_FRICTION

        state.linearVelocity = Vec3(newVx, state.linearVelocity.y, newVz)
        state.angularVelocity = Vec3(newOmegaX, newOmegaY, newOmegaZ)
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
        state.wallBounceCooldown = 0
    }
}
