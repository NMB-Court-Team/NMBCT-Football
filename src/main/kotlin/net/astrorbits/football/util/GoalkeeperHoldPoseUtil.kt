package net.astrorbits.football.util

import net.astrorbits.football.input.GoalkeeperInputConfig
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

/**
 * 守门员持球时的球心/实体位置计算。
 * 球置于身前偏低处（约腰–胸高度），避免第一人称挡视野。
 *
 * 第三人称/服务端权威位置使用 [Entity.getPreciseBodyRotation]（身体朝向），
 * 与玩家模型及伸臂动画一致；第一人称本地渲染单独使用视线方向。
 */
object GoalkeeperHoldPoseUtil {
    fun computeBallCenter(player: Entity): Vec3 {
        val forward = forwardFromBodyYaw(player, 1.0f)
        val height = holdHeight(player)
        val base = player.position()
        return Vec3(
            base.x + forward.x * GoalkeeperInputConfig.GK_HOLD_FORWARD,
            base.y + height,
            base.z + forward.z * GoalkeeperInputConfig.GK_HOLD_FORWARD,
        )
    }

    fun computeBallEntityPos(player: Entity): Vec3 {
        val center = computeBallCenter(player)
        return Vec3(center.x, center.y - FootballPhysicsConfig.RADIUS, center.z)
    }

    /** 沿固定水平方向持球（球门球放球阶段：球在身前靠场内一侧，避免落在靠球门一侧）。 */
    fun computeBallEntityPosFieldward(player: Entity, fieldward: Vec3): Vec3 {
        val fwd = Vec3(fieldward.x, 0.0, fieldward.z)
        val lenSq = fwd.lengthSqr()
        val horiz = if (lenSq < 1.0e-8) Vec3(0.0, 0.0, 1.0) else fwd.scale(1.0 / kotlin.math.sqrt(lenSq))
        val height = holdHeight(player)
        val base = player.position()
        val center = Vec3(
            base.x + horiz.x * GoalkeeperInputConfig.GK_HOLD_FORWARD,
            base.y + height,
            base.z + horiz.z * GoalkeeperInputConfig.GK_HOLD_FORWARD,
        )
        return Vec3(center.x, center.y - FootballPhysicsConfig.RADIUS, center.z)
    }

    /** 开球前球心位置：与第一人称持球渲染一致，沿视线水平方向。 */
    fun computeThrowReleaseEntityPos(player: Entity, lookYaw: Float, lookPitch: Float): Vec3 {
        val forward = forwardFromLookYawPitch(lookYaw, lookPitch)
        val height = holdHeight(player) - GoalkeeperInputConfig.GK_HOLD_FIRST_PERSON_EXTRA_DOWN
        val forwardDistance = GoalkeeperInputConfig.GK_HOLD_FORWARD +
            GoalkeeperInputConfig.GK_HOLD_FIRST_PERSON_EXTRA_FORWARD
        val base = player.position()
        val center = Vec3(
            base.x + forward.x * forwardDistance,
            base.y + height,
            base.z + forward.z * forwardDistance,
        )
        return Vec3(center.x, center.y - FootballPhysicsConfig.RADIUS, center.z)
    }

    fun computeBallCenterInterpolated(player: Entity, partialTick: Float): Vec3 {
        val forward = forwardFromBodyYaw(player, partialTick)
        val height = holdHeight(player)
        val x = Mth.lerp(partialTick.toDouble(), player.xOld, player.x)
        val y = Mth.lerp(partialTick.toDouble(), player.yOld, player.y)
        val z = Mth.lerp(partialTick.toDouble(), player.zOld, player.z)
        return Vec3(
            x + forward.x * GoalkeeperInputConfig.GK_HOLD_FORWARD,
            y + height,
            z + forward.z * GoalkeeperInputConfig.GK_HOLD_FORWARD,
        )
    }

    fun computeBallEntityPosInterpolated(player: Entity, partialTick: Float): Vec3 {
        val center = computeBallCenterInterpolated(player, partialTick)
        return Vec3(center.x, center.y - FootballPhysicsConfig.RADIUS, center.z)
    }

    /**
     * 持球朝向：与位置相同，使用同步后的 yRot；第一人称渲染可附加 pitch。
     */
    fun computeHeldOrientation(player: Entity, partialTick: Float, firstPersonLook: Boolean = false): Quaternionf {
        val yaw = player.getViewYRot(partialTick)
        val pitch = if (firstPersonLook) player.getViewXRot(partialTick) else 0f
        return orientationFromYawPitch(yaw, pitch)
    }

    fun computeHeldOrientationFromLook(lookYaw: Float, lookPitch: Float): Quaternionf =
        orientationFromYawPitch(lookYaw, lookPitch)

    /**
     * 持球期间将身体 yaw 对齐到头部 yaw（仅客户端本地使用）。
     * 服务端勿调用：服务端 yHeadRot 可能滞后，会覆盖客户端同步过来的 yRot。
     */
    fun alignBodyToHead(entity: Entity) {
        if (entity !is LivingEntity) {
            return
        }
        val headYaw = entity.yHeadRot
        entity.yRot = headYaw
        entity.yRotO = headYaw
        entity.yBodyRot = headYaw
        entity.yBodyRotO = headYaw
    }

    /** 第一人称时略向前/向下偏移，并沿视线放置，避免球模型遮挡准星附近视野。 */
    fun computeFirstPersonHoldPos(player: Entity, partialTick: Float): Vec3 {
        val forward = forwardFromLook(player)
        val height = holdHeight(player) - GoalkeeperInputConfig.GK_HOLD_FIRST_PERSON_EXTRA_DOWN
        val forwardDistance = GoalkeeperInputConfig.GK_HOLD_FORWARD +
            GoalkeeperInputConfig.GK_HOLD_FIRST_PERSON_EXTRA_FORWARD
        val x = Mth.lerp(partialTick.toDouble(), player.xOld, player.x)
        val y = Mth.lerp(partialTick.toDouble(), player.yOld, player.y)
        val z = Mth.lerp(partialTick.toDouble(), player.zOld, player.z)
        val center = Vec3(
            x + forward.x * forwardDistance,
            y + height,
            z + forward.z * forwardDistance,
        )
        return Vec3(center.x, center.y - FootballPhysicsConfig.RADIUS, center.z)
    }

    /**
     * 水平前向：持球期间客户端每 tick 将 [Entity.yRot] 对齐头部，
     * 服务端以同步后的 yRot 为准（勿用 [Entity.getPreciseBodyRotation]，yBodyRot 会滞后）。
     */
    private fun forwardFromBodyYaw(player: Entity, partialTick: Float): Vec3 =
        forwardFromYaw(player.getViewYRot(partialTick))

    private fun forwardFromYaw(yawDeg: Float): Vec3 {
        val yawRad = Math.toRadians(yawDeg.toDouble())
        return Vec3(-kotlin.math.sin(yawRad), 0.0, kotlin.math.cos(yawRad))
    }

    /** 头部视线水平朝向（第一人称渲染与开球落点）。 */
    private fun forwardFromLook(player: Entity): Vec3 {
        val horizontal = Vec3Math.horizontal(player.lookAngle)
        return Vec3Math.normalizeSafe(horizontal, Vec3(0.0, 0.0, 1.0))
    }

    private fun forwardFromLookYawPitch(lookYaw: Float, lookPitch: Float): Vec3 {
        val pitchRad = Math.toRadians(lookPitch.toDouble())
        val yawRad = Math.toRadians(-lookYaw.toDouble())
        val cosPitch = kotlin.math.cos(pitchRad)
        val horizontal = Vec3(
            kotlin.math.sin(yawRad) * cosPitch,
            0.0,
            kotlin.math.cos(yawRad) * cosPitch,
        )
        return Vec3Math.normalizeSafe(horizontal, forwardFromYaw(lookYaw))
    }

    private fun holdHeight(player: Entity): Double {
        var height = GoalkeeperInputConfig.GK_HOLD_HEIGHT
        if (player.isShiftKeyDown) {
            height -= GoalkeeperInputConfig.GK_HOLD_CROUCH_HEIGHT_OFFSET
        }
        return height
    }

    private fun orientationFromYawPitch(yawDeg: Float, pitchDeg: Float): Quaternionf {
        val yawRad = Math.toRadians(-yawDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
        return Quaternionf().rotateY(yawRad).rotateX(pitchRad)
    }
}
