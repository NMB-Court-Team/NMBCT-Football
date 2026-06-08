package net.astrorbits.football.util

import net.astrorbits.football.match.GoalConfig
import net.astrorbits.football.match.KickPosition
import net.astrorbits.football.match.MatchConfig
import net.astrorbits.football.match.MatchConfigHolder
import net.astrorbits.football.match.TeamSide
import net.astrorbits.football.match.goalCenter
import net.astrorbits.football.match.penaltyKickBehindBall
import net.astrorbits.football.match.toVec3
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

object GoalCrossingUtil {
    private const val GOAL_Z_TOLERANCE = 1.01
    /** 球心越过门线进入球网侧的最小有符号距离。 */
    private const val GOAL_LINE_INSIDE_EPSILON = 0.05
    const val KICK_TOWARD_GOAL_DOT_THRESHOLD = 0.35

    data class GoalLineCrossing(
        val intersection: Vec3,
        val inGoal: Boolean,
        /**
         * 穿越门线但未进框：含门柱外、横梁上/下等，进入 [MatchState.pendingGoalLineOut] 待确认；
         * 确认期内球弹入门框则改判进球，否则判角球/球门球。
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

        val linePoint = goal.goalCenter()
        val d1 = signedDistance(prevCenter, linePoint.x, linePoint.y, linePoint.z, facing)
        val d2 = signedDistance(currCenter, linePoint.x, linePoint.y, linePoint.z, facing)

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
        val linePoint = goal.goalCenter()
        if (signedDistance(center, linePoint.x, linePoint.y, linePoint.z, facing) <= GOAL_LINE_INSIDE_EPSILON) {
            return false
        }
        return isPointInGoalFrame(goal, center.x, center.y, center.z)
    }

    fun segmentEnteredGoal(goal: GoalConfig, prevCenter: Vec3, currCenter: Vec3): Boolean =
        !isCenterInGoal(goal, prevCenter) && isCenterInGoal(goal, currCenter)

    fun isPastGoalLineOnNetSide(goal: GoalConfig, center: Vec3): Boolean {
        val facing = Vec3(goal.facingX, goal.facingY, goal.facingZ)
        if (facing.lengthSqr() < 1e-6) {
            return false
        }
        val line = goal.goalCenter()
        return signedDistance(center, line.x, line.y, line.z, facing) > GOAL_LINE_INSIDE_EPSILON
    }

    /** 球心已越过门线且不在门框内（含横梁上飞过、门柱外等）。 */
    fun isGoalLineMiss(goal: GoalConfig, center: Vec3): Boolean =
        isPastGoalLineOnNetSide(goal, center) &&
            !isPointInGoalFrame(goal, center.x, center.y, center.z)

    fun isPointInGoalFrame(goal: GoalConfig, x: Double, y: Double, z: Double): Boolean {
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

    private fun isDefiniteGoalLineOutMiss(goal: GoalConfig, ix: Double, iy: Double, iz: Double): Boolean {
        val minX = minOf(goal.x1, goal.x2)
        val maxX = maxOf(goal.x1, goal.x2)
        val minY = minOf(goal.y1, goal.y2)
        val maxY = maxOf(goal.y1, goal.y2)
        val minZ = minOf(goal.z1, goal.z2)
        val maxZ = maxOf(goal.z1, goal.z2)
        val besidePost = ix !in minX..maxX
        val besideNet = iz !in minZ - GOAL_Z_TOLERANCE..maxZ + GOAL_Z_TOLERANCE
        val betweenPosts = ix in minX..maxX
        val aboveBar = betweenPosts && iy > maxY
        val belowFrame = betweenPosts && iy < minY
        return besidePost || besideNet || aboveBar || belowFrame
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

    private const val GOAL_KICK_BALL_FIELDWARD_OFFSET = 1.5

    fun goalKickRestartPosition(goal: GoalConfig): Vec3 {
        val snapped = snapRestartBallToFieldSide(goal, goal.goalKick)
        val fieldward = goal.penaltyKickBehindBall()
        return Vec3(
            snapped.x + fieldward.x * GOAL_KICK_BALL_FIELDWARD_OFFSET,
            snapped.y + fieldward.y * GOAL_KICK_BALL_FIELDWARD_OFFSET,
            snapped.z + fieldward.z * GOAL_KICK_BALL_FIELDWARD_OFFSET,
        )
    }

    /**
     * 按 IFAB：角球在球离开场地一侧、距出界点最近的角旗区发球。
     * 「右」为面向球门（沿 [facing]）时的右侧，而非固定世界坐标正负。
     */
    fun cornerKickPosition(goal: GoalConfig, facing: Vec3, ix: Double, iz: Double): Vec3 {
        val goalCenterX = (goal.x1 + goal.x2) / 2.0
        val goalCenterZ = (goal.z1 + goal.z2) / 2.0
        val onRight = if (abs(facing.x) > abs(facing.z)) {
            (iz - goalCenterZ) * facing.x > 0.0
        } else {
            (ix - goalCenterX) * facing.z > 0.0
        }
        val corner = if (onRight) goal.cornerKickRight else goal.cornerKickLeft
        return snapRestartBallToFieldSide(goal, corner)
    }

    /**
     * 将门球/角球摆球点钳制到门线场内一侧。
     * [facing] 为门线法向（指向球网侧）；配置点若落在门内或门线网侧则沿法向拉回场内。
     */
    fun snapRestartBallToFieldSide(goal: GoalConfig, position: KickPosition): Vec3 {
        val facing = Vec3(goal.facingX, goal.facingY, goal.facingZ)
        val lenSq = facing.lengthSqr()
        if (lenSq < 1e-6) {
            return position.toVec3()
        }
        val invLen = 1.0 / sqrt(lenSq)
        val fnx = facing.x * invLen
        val fny = facing.y * invLen
        val fnz = facing.z * invLen
        val line = goal.goalCenter()
        val pos = position.toVec3()
        val signedOffset = (pos.x - line.x) * fnx + (pos.y - line.y) * fny + (pos.z - line.z) * fnz
        val fieldMargin = FootballPhysicsConfig.RADIUS + 0.05
        val targetMaxOffset = -fieldMargin
        if (signedOffset <= targetMaxOffset) {
            return pos
        }
        val pullBack = signedOffset - targetMaxOffset
        return Vec3(
            pos.x - fnx * pullBack,
            pos.y - fny * pullBack,
            pos.z - fnz * pullBack,
        )
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
