package net.astrorbits.football.util

import kotlin.math.abs
import net.astrorbits.football.match.GoalConfig
import net.astrorbits.football.match.MatchConfig
import net.astrorbits.football.match.MatchConfigHolder
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.TeamSide
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

object GoalCrossingUtil {
    private const val GOAL_Z_TOLERANCE = 1.01
    /** 球心越过门线进入球网侧的最小有符号距离。 */
    private const val GOAL_LINE_INSIDE_EPSILON = 0.05
    const val KICK_TOWARD_GOAL_DOT_THRESHOLD = 0.35

    data class GoalLineCrossing(
        val intersection: Vec3,
        val inGoal: Boolean,
        /**
         * 穿越点落在门框外且属于「门柱外侧」类出界（非仅高出/低于横梁或地面）。
         * 打框弹入球门时穿越点常在横梁高度外，不应据此立即判底线出界。
         */
        val definiteGoalLineOut: Boolean,
        val defendingTeam: TeamSide,
        val attackingTeam: TeamSide,
    )

    data class GoalPrediction(
        val wouldScore: Boolean,
        val attackingTeam: TeamSide?,
    )

    fun segmentCrossesGoalLine(
        goal: GoalConfig,
        prevCenter: Vec3,
        currCenter: Vec3,
        defendingTeam: TeamSide,
        attackingTeam: TeamSide,
    ): GoalLineCrossing? {
        val facing = Vec3(goal.facingX, goal.facingY, goal.facingZ)
        if (facing.lengthSqr() < 1e-6) {
            return null
        }

        val refX = goal.x1 + facing.x
        val refY = goal.y1 + facing.y
        val refZ = goal.z1 + facing.z
        val d1 = signedDistance(prevCenter, refX, refY, refZ, facing)
        val d2 = signedDistance(currCenter, refX, refY, refZ, facing)

        if (d1 * d2 >= 0) {
            return null
        }
        if (d2 - d1 <= 0) {
            return null
        }

        val t = d1 / (d1 - d2)
        val movement = currCenter.subtract(prevCenter)
        val ix = prevCenter.x + movement.x * t
        val iy = prevCenter.y + movement.y * t
        val iz = prevCenter.z + movement.z * t

        val inGoal = isPointInGoalFrame(goal, ix, iy, iz)
        val definiteGoalLineOut = !inGoal && isDefiniteGoalLineOutMiss(goal, ix, iy, iz)

        return GoalLineCrossing(
            intersection = Vec3(ix, iy, iz),
            inGoal = inGoal,
            definiteGoalLineOut = definiteGoalLineOut,
            defendingTeam = defendingTeam,
            attackingTeam = attackingTeam,
        )
    }

    /** 球心已在门线内侧且位于门框（含 Z 容差）内，视为已进门。 */
    fun isCenterInGoal(goal: GoalConfig, center: Vec3): Boolean {
        val facing = Vec3(goal.facingX, goal.facingY, goal.facingZ)
        if (facing.lengthSqr() < 1e-6) {
            return false
        }
        val refX = goal.x1 + facing.x
        val refY = goal.y1 + facing.y
        val refZ = goal.z1 + facing.z
        if (signedDistance(center, refX, refY, refZ, facing) <= GOAL_LINE_INSIDE_EPSILON) {
            return false
        }
        return isPointInGoalFrame(goal, center.x, center.y, center.z)
    }

    fun segmentEnteredGoal(goal: GoalConfig, prevCenter: Vec3, currCenter: Vec3): Boolean =
        !isCenterInGoal(goal, prevCenter) && isCenterInGoal(goal, currCenter)

    private fun isPointInGoalFrame(goal: GoalConfig, x: Double, y: Double, z: Double): Boolean {
        val minX = minOf(goal.x1, goal.x2)
        val maxX = maxOf(goal.x1, goal.x2)
        val minY = minOf(goal.y1, goal.y2)
        val maxY = maxOf(goal.y1, goal.y2)
        val minZ = minOf(goal.z1, goal.z2)
        val maxZ = maxOf(goal.z1, goal.z2)
        return x in minX..maxX
            && y in minY..maxY
            && z in minZ - GOAL_Z_TOLERANCE..maxZ + GOAL_Z_TOLERANCE
    }

    /** 仅门柱左右外侧算明确出界；仅高出/低于门框仍可能弹入，不算。 */
    private fun isDefiniteGoalLineOutMiss(goal: GoalConfig, ix: Double, iy: Double, iz: Double): Boolean {
        val minX = minOf(goal.x1, goal.x2)
        val maxX = maxOf(goal.x1, goal.x2)
        val minZ = minOf(goal.z1, goal.z2)
        val maxZ = maxOf(goal.z1, goal.z2)
        val besidePost = ix !in minX..maxX
        val besideNet = iz !in minZ - GOAL_Z_TOLERANCE..maxZ + GOAL_Z_TOLERANCE
        return besidePost || besideNet
    }

    /** 检测本段位移是否穿过任一门框并进门。 */
    fun predictGoalFromSegment(
        config: MatchConfig,
        prevCenter: Vec3,
        currCenter: Vec3,
    ): GoalLineCrossing? {
        segmentCrossesGoalLine(config.goalA, prevCenter, currCenter, TeamSide.A, TeamSide.B)?.let {
            if (it.inGoal) {
                return it
            }
        }
        segmentCrossesGoalLine(config.goalB, prevCenter, currCenter, TeamSide.B, TeamSide.A)?.let {
            if (it.inGoal) {
                return it
            }
        }
        return null
    }

    fun opponentGoalForTeam(team: TeamSide, config: MatchConfig): GoalConfig = when (team) {
        TeamSide.A -> config.goalB
        TeamSide.B -> config.goalA
    }

    fun isKickTowardOpponentGoal(
        player: ServerPlayer,
        ballCenter: Vec3,
        kickDirection: Vec3,
    ): Boolean {
        val team = net.astrorbits.football.match.MatchParticipation.participatingTeam(player) ?: return false
        val goal = opponentGoalForTeam(team, MatchConfigHolder.current)
        val goalCenter = Vec3(
            (goal.x1 + goal.x2) / 2.0,
            (goal.y1 + goal.y2) / 2.0,
            (goal.z1 + goal.z2) / 2.0,
        )
        val towardGoal = Vec3Math.horizontal(goalCenter.subtract(ballCenter))
        val kickHoriz = Vec3Math.horizontal(kickDirection)
        if (towardGoal.lengthSqr() < 1e-8 || kickHoriz.lengthSqr() < 1e-8) {
            return false
        }
        return towardGoal.normalize().dot(kickHoriz.normalize()) >= KICK_TOWARD_GOAL_DOT_THRESHOLD
    }

    fun cornerKickPosition(goal: GoalConfig, facing: Vec3, ix: Double, iz: Double): Vec3 {
        val gx1 = goal.x1
        val gz1 = goal.z1
        val gx2 = goal.x2
        val gz2 = goal.z2
        val goalCenterX = (gx1 + gx2) / 2.0
        val goalCenterZ = (gz1 + gz2) / 2.0
        val onRight = if (abs(facing.x) > abs(facing.z)) {
            iz > goalCenterZ
        } else {
            ix > goalCenterX
        }
        val corner = if (onRight) goal.cornerKickRight else goal.cornerKickLeft
        return Vec3(corner.x, corner.y, corner.z)
    }

    private fun signedDistance(
        point: Vec3,
        refX: Double,
        refY: Double,
        refZ: Double,
        facing: Vec3,
    ): Double =
        (point.x - refX) * facing.x + (point.y - refY) * facing.y + (point.z - refZ) * facing.z
}
