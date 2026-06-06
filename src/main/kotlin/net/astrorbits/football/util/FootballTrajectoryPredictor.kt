package net.astrorbits.football.util

import net.astrorbits.football.match.MatchConfig
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.physics.FootballPhysicsState
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import kotlin.math.abs

object FootballTrajectoryPredictor {
    private const val MAX_TICKS = 160
    private const val EARLY_EXIT_SPEED_SQR = 1.0e-8
    private const val COLLISION_EPSILON = 1.0e-3

    fun predictWouldScore(
        level: ServerLevel,
        bottomPos: Vec3,
        state: FootballPhysicsState,
        config: MatchConfig,
    ): GoalCrossingUtil.GoalPrediction {
        val simState = copyState(state)
        var pos = bottomPos
        val radius = FootballPhysicsConfig.RADIUS

        repeat(MAX_TICKS) {
            val prevCenter = pos.add(0.0, radius, 0.0)
            pos = simulateTick(level, pos, simState)
            val currCenter = pos.add(0.0, radius, 0.0)

            GoalCrossingUtil.predictGoalFromSegment(config, prevCenter, currCenter)?.let { crossing ->
                return GoalCrossingUtil.GoalPrediction(
                    wouldScore = true,
                    attackingTeam = crossing.attackingTeam,
                )
            }

            if (simState.linearVelocity.lengthSqr() < EARLY_EXIT_SPEED_SQR &&
                simState.angularVelocity.lengthSqr() < EARLY_EXIT_SPEED_SQR
            ) {
                return GoalCrossingUtil.GoalPrediction(wouldScore = false, attackingTeam = null)
            }
        }
        return GoalCrossingUtil.GoalPrediction(wouldScore = false, attackingTeam = null)
    }

    fun copyState(state: FootballPhysicsState): FootballPhysicsState =
        FootballPhysicsState(
            linearVelocity = state.linearVelocity,
            angularVelocity = state.angularVelocity,
            onGround = state.onGround,
            inCobweb = state.inCobweb,
            orientation = Quaternionf(state.orientation),
            wallBounceCooldown = state.wallBounceCooldown,
        )

    private fun simulateTick(level: Level, bottomPos: Vec3, state: FootballPhysicsState): Vec3 {
        val radius = FootballPhysicsConfig.RADIUS
        FootballPhysicsSimulator.applyAirForces(state)
        val center = bottomPos.add(0.0, radius, 0.0)
        val intended = state.linearVelocity
        var newCenter = center.add(intended)

        val depen = FootballBlockDepenetration.depenetrateSphere(level, newCenter, radius)
        newCenter = depen.center
        val correction = depen.correction

        val horizontalCollision = abs(correction.x) > COLLISION_EPSILON || abs(correction.z) > COLLISION_EPSILON
        val verticalCollisionBelow = correction.y > COLLISION_EPSILON
        val actualMotion = newCenter.subtract(center)
        val onGroundContact = verticalCollisionBelow && intended.y <= 0.0

        FootballPhysicsSimulator.resolveCollisions(
            state,
            horizontalCollision,
            verticalCollisionBelow,
            onGroundContact || state.onGround,
            intended,
            actualMotion,
        )

        val box = AABB.ofSize(newCenter, radius * 2.0, radius * 2.0, radius * 2.0)
        if (CobwebUtil.isIntersectingCobweb(level, box)) {
            CobwebUtil.applyCobwebDrag(state)
        } else {
            state.inCobweb = false
        }

        return newCenter.subtract(0.0, radius, 0.0)
    }
}
