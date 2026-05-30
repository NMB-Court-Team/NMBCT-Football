package net.astrorbits.football.input

import net.astrorbits.football.Football
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.util.FootballKickUtil
import net.astrorbits.football.util.Vec3Math
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3

object FootballDribbleAssist {
    /**
     * 对运球 session 中的足球施加 PD 辅助控制。
     *
     * @return 控制是否仍有效；false 表示应结束 session（超距、实体无效等）
     */
    fun apply(player: ServerPlayer, football: Football, dribbleBaseYaw: Float? = null): Boolean {
        if (player.mainHandItem.isEmpty.not()) {
            return false
        }

        val horizontalDistanceSqr = player.distanceToSqr(football)
        if (horizontalDistanceSqr > FootballInputConfig.DRIBBLE_MAX_CONTROL_RANGE * FootballInputConfig.DRIBBLE_MAX_CONTROL_RANGE) {
            return false
        }

        val moveDir = FootballKickUtil.resolveDribbleDirection(player, dribbleBaseYaw)
        if (moveDir.lengthSqr() < 1.0e-8) {
            return true
        }

        val dir = Vec3Math.normalizeSafe(moveDir)
        val playerPos = player.position()
        val target = Vec3(
            playerPos.x + dir.x * FootballInputConfig.DRIBBLE_TARGET_DISTANCE,
            football.y + FootballPhysicsConfig.RADIUS,
            playerPos.z + dir.z * FootballInputConfig.DRIBBLE_TARGET_DISTANCE
        )

        val ballCenter = football.position().add(0.0, FootballPhysicsConfig.RADIUS, 0.0)
        val physicsState = football.getPhysicsState()
        val currentHoriz = Vec3Math.horizontal(physicsState.linearVelocity)

        val posError = Vec3Math.horizontal(target.subtract(ballCenter))
        var positionGain = FootballInputConfig.DRIBBLE_POSITION_GAIN
        if (!physicsState.onGround) {
            positionGain *= FootballInputConfig.DRIBBLE_AIR_POSITION_SCALE
        }

        val playerSpeed = resolvePlayerHorizontalSpeed(player)
        val desiredVel = dir.scale(playerSpeed * FootballInputConfig.DRIBBLE_SPEED_MATCH)
        val velError = desiredVel.subtract(currentHoriz)

        var correction = posError.scale(positionGain).add(velError.scale(FootballInputConfig.DRIBBLE_VELOCITY_GAIN))
        correction = applyLateralDamping(correction, dir)

        val correctionLength = correction.length()
        if (correctionLength > FootballInputConfig.DRIBBLE_MAX_CORRECTION) {
            correction = correction.scale(FootballInputConfig.DRIBBLE_MAX_CORRECTION / correctionLength)
        }

        val newHoriz = currentHoriz.add(correction)
        football.applyDribbleAssist(newHoriz)
        return true
    }

    private fun applyLateralDamping(correction: Vec3, dir: Vec3): Vec3 {
        val along = dir.scale(correction.dot(dir))
        val lateral = correction.subtract(along).scale(FootballInputConfig.DRIBBLE_LATERAL_GAIN)
        return along.add(lateral)
    }

    private fun resolvePlayerHorizontalSpeed(player: ServerPlayer): Double {
        val intent = Vec3Math.horizontal(player.lastClientMoveIntent)
        if (intent.lengthSqr() > 1.0e-4) {
            val baseSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED)
            val sprintScale = if (player.isSprinting) 1.3 else 1.0
            return baseSpeed * sprintScale * intent.length().coerceIn(0.0, 1.0)
        }

        return Vec3Math.horizontal(player.deltaMovement).length()
    }
}
