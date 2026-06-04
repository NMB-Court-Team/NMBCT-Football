package net.astrorbits.football.input

import net.astrorbits.football.Football
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.stamina.BoostSprintState
import net.astrorbits.football.util.FootballKickUtil
import net.astrorbits.football.util.Vec3Math
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import kotlin.math.min

object FootballDribbleAssist {
    /** 滑铲带球：目标点沿铲向略领先，减轻“球总在身后追”；不宜过大否则球位偏前 */
    private const val SLIDE_TARGET_LEAD_TICKS = 0.72
    /** 滑铲带球：沿铲向回拉（格），球位略靠后 */
    private const val SLIDE_TARGET_REAR_OFFSET = 0.1
    /** 落后时沿铲向可叠加的追位速度上限（blocks/tick） */
    private const val SLIDE_CATCHUP_SPEED_CAP = 2.0
    /** 落后追位：位置误差 → 额外沿向速度的增益 */
    private const val SLIDE_CATCHUP_GAIN = 1.1
    /** 横向纠偏强度（相对普通带球的 lateral gain） */
    private const val SLIDE_LATERAL_GAIN = 1.15
    private const val SLIDE_MAX_SPEED_FACTOR = 1.35

    /** 加速冲刺带球：目标点沿移动方向超前（运球在 START tick 更新，需补偿本 tick 位移） */
    private const val BOOST_TARGET_LEAD_TICKS = 0.55
    /** 超前后再沿移动方向回拉（格），避免球位过前 */
    private const val BOOST_TARGET_REAR_OFFSET = 0.12
    /** 球落后目标时沿向追位上限（blocks/tick） */
    private const val BOOST_CATCHUP_SPEED_CAP = 1.0
    private const val BOOST_CATCHUP_GAIN = 0.75
    /** 在配置 [DRIBBLE_SPEED_MATCH] 上叠加，上限 1.0 */
    private const val BOOST_SPEED_MATCH_BONUS = 0.06
    private const val BOOST_MAX_CORRECTION_FACTOR = 1.6

    /**
     * 对运球 session 中的足球施加 PD 辅助控制。
     *
     * @return 控制是否仍有效；false 表示应结束 session（超距、实体无效等）
     */
    fun apply(player: ServerPlayer, football: Football, dribbleBaseYaw: Float? = null): Boolean {
        if (player.mainHandItem.isEmpty.not()) {
            return false
        }
        if (football.isPlayerBallMovementForbidden(player)) {
            return false
        }
        if (football.isHoldStealProtectedFrom(player)) {
            return false
        }

        val horizontalDistanceSqr = player.distanceToSqr(football)
        if (horizontalDistanceSqr > FootballInputConfig.DRIBBLE_MAX_CONTROL_RANGE * FootballInputConfig.DRIBBLE_MAX_CONTROL_RANGE) {
            return false
        }

        val slideVelocity = SlideTackleSessions.effectiveHorizontalVelocity(player)
        val moveDir = if (slideVelocity != null && slideVelocity.lengthSqr() > 1.0e-8) {
            slideVelocity
        } else {
            FootballKickUtil.resolveDribbleDirection(player, dribbleBaseYaw)
        }
        if (moveDir.lengthSqr() < 1.0e-8) {
            return true
        }

        val dir = Vec3Math.normalizeSafe(moveDir)
        val playerPos = player.position()
        val targetDistance = FootballInputConfig.DRIBBLE_TARGET_DISTANCE
        var target = Vec3(
            playerPos.x + dir.x * targetDistance,
            football.y + FootballPhysicsConfig.RADIUS,
            playerPos.z + dir.z * targetDistance,
        )
        if (slideVelocity != null && slideVelocity.lengthSqr() > 1.0e-8) {
            target = target
                .add(slideVelocity.scale(SLIDE_TARGET_LEAD_TICKS))
                .subtract(dir.scale(SLIDE_TARGET_REAR_OFFSET))
        } else if (BoostSprintState.isActive(player.uuid)) {
            val leadVel = FootballMovementInputUtil.bestHorizontalVelocity(player)
            if (leadVel.lengthSqr() > 1.0e-8) {
                target = target
                    .add(leadVel.scale(BOOST_TARGET_LEAD_TICKS))
                    .subtract(dir.scale(BOOST_TARGET_REAR_OFFSET))
            }
        }

        val ballCenter = football.position().add(0.0, FootballPhysicsConfig.RADIUS, 0.0)
        if (slideVelocity != null && slideVelocity.lengthSqr() > 1.0e-8) {
            applySlideDribble(football, player, dir, slideVelocity, ballCenter, target)
            return true
        }

        val boostActive = BoostSprintState.isActive(player.uuid)
        applyNormalDribble(football, player, dir, ballCenter, target, boostActive)
        return true
    }

    private fun applySlideDribble(
        football: Football,
        player: ServerPlayer,
        dir: Vec3,
        slideVelocity: Vec3,
        ballCenter: Vec3,
        target: Vec3,
    ) {
        val slideSpeed = slideVelocity.length()
        val posError = Vec3Math.horizontal(target.subtract(ballCenter))
        val alongError = posError.dot(dir)
        val behind = alongError.coerceAtLeast(0.0)
        val catchUp = min(behind * SLIDE_CATCHUP_GAIN, SLIDE_CATCHUP_SPEED_CAP)
        var alongSpeed = slideSpeed + catchUp
        val maxAlong = maxOf(slideSpeed * SLIDE_MAX_SPEED_FACTOR, slideSpeed + behind * 0.35)
        alongSpeed = alongSpeed.coerceAtMost(maxAlong)

        val lateralError = posError.subtract(dir.scale(posError.dot(dir)))
        val lateralVel = lateralError.scale(SLIDE_LATERAL_GAIN)
        var newHoriz = dir.scale(alongSpeed).add(lateralVel)

        val maxTotal = maxOf(slideSpeed * SLIDE_MAX_SPEED_FACTOR, alongSpeed + lateralVel.length() * 0.25)
        val totalLen = newHoriz.length()
        if (totalLen > maxTotal && totalLen > 1.0e-8) {
            newHoriz = newHoriz.scale(maxTotal / totalLen)
        }

        football.applyDribbleAssist(newHoriz, player)
    }

    private fun applyNormalDribble(
        football: Football,
        player: ServerPlayer,
        dir: Vec3,
        ballCenter: Vec3,
        target: Vec3,
        boostActive: Boolean = false,
    ) {
        val physicsState = football.getPhysicsState()
        val currentHoriz = Vec3Math.horizontal(physicsState.linearVelocity)

        val posError = Vec3Math.horizontal(target.subtract(ballCenter))
        var positionGain = FootballInputConfig.DRIBBLE_POSITION_GAIN
        if (!physicsState.onGround) {
            positionGain *= FootballInputConfig.DRIBBLE_AIR_POSITION_SCALE
        }

        val playerSpeed = resolvePlayerHorizontalSpeed(player, boostActive)
        var speedMatch = FootballInputConfig.DRIBBLE_SPEED_MATCH
        if (boostActive) {
            speedMatch = min(1.0, speedMatch + BOOST_SPEED_MATCH_BONUS)
        }
        var alongDesired = playerSpeed * speedMatch
        if (boostActive) {
            val behind = posError.dot(dir).coerceAtLeast(0.0)
            alongDesired += min(behind * BOOST_CATCHUP_GAIN, BOOST_CATCHUP_SPEED_CAP)
        }
        val desiredVel = dir.scale(alongDesired)
        val velError = desiredVel.subtract(currentHoriz)

        var correction = posError.scale(positionGain).add(velError.scale(FootballInputConfig.DRIBBLE_VELOCITY_GAIN))
        correction = applyLateralDamping(correction, dir)

        var maxCorrection = FootballInputConfig.DRIBBLE_MAX_CORRECTION
        if (boostActive) {
            maxCorrection *= BOOST_MAX_CORRECTION_FACTOR
        }
        val correctionLength = correction.length()
        if (correctionLength > maxCorrection) {
            correction = correction.scale(maxCorrection / correctionLength)
        }

        val newHoriz = currentHoriz.add(correction)
        football.applyDribbleAssist(newHoriz, player)
    }

    private fun applyLateralDamping(correction: Vec3, dir: Vec3): Vec3 {
        val along = dir.scale(correction.dot(dir))
        val lateral = correction.subtract(along).scale(FootballInputConfig.DRIBBLE_LATERAL_GAIN)
        return along.add(lateral)
    }

    private fun resolvePlayerHorizontalSpeed(player: ServerPlayer, boostActive: Boolean = false): Double {
        if (boostActive) {
            val best = FootballMovementInputUtil.bestHorizontalVelocity(player)
            if (best.lengthSqr() > 1.0e-4) {
                return best.length()
            }
        }

        val intent = Vec3Math.horizontal(player.lastClientMoveIntent)
        if (intent.lengthSqr() > 1.0e-4) {
            return FootballMovementInputUtil.intendedHorizontalSpeed(
                player,
                intent.length().coerceIn(0.0, 1.0),
            )
        }

        return FootballMovementInputUtil.measuredHorizontalVelocity(player).length()
    }
}
