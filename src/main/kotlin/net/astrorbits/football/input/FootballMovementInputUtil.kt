package net.astrorbits.football.input

import net.astrorbits.football.match.MatchState
import net.astrorbits.football.util.Vec3Math
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

object FootballMovementInputUtil {
    fun hasMovementInput(player: Player): Boolean {
        return movementInputVector(player).lengthSqr() > 1.0e-4
    }

    fun hasMovementInput(player: Player, movementYaw: Float): Boolean {
        return movementInputVector(player, movementYaw).lengthSqr() > 1.0e-4
    }

    /**
     * 疾跑/加速疾跑体力消耗与激活用：有水平移动意图，且相对 [Player.yRot] 朝向前方分量足够大。
     * 开球锁定等待期间仅要求有移动输入（便于 reposition），不强制“朝面向方向”前进。
     */
    fun hasSprintForwardImpulse(player: ServerPlayer): Boolean {
        val intent = movementInputVector(player)
        if (intent.lengthSqr() <= 1.0e-4) {
            return false
        }
        if (MatchState.isKickoffInteractionLocked(player)) {
            return true
        }
        val yawRad = Math.toRadians(player.yRot.toDouble())
        val forwardX = -kotlin.math.sin(yawRad)
        val forwardZ = kotlin.math.cos(yawRad)
        return intent.x * forwardX + intent.z * forwardZ > 0.1
    }

    /**
     * 水平移动意图（世界坐标），长度约 0~1。
     * 服务端使用 [ServerPlayer.lastClientMoveIntent]；客户端回退到 xxa/zza 换算。
     *
     * @param movementYaw 移动基准朝向；为 null 时使用 [Player.yRot]。
     */
    fun movementInputVector(player: Player, movementYaw: Float? = null): Vec3 {
        if (player is ServerPlayer) {
            val intent = horizontalMoveIntent(player.lastClientMoveIntent)
            if (movementYaw != null && intent.lengthSqr() > 1.0e-4) {
                return rebaseHorizontalMovement(intent, player.yRot, movementYaw)
            }
            return intent
        }

        val moveX = player.xxa.toDouble()
        val moveZ = player.zza.toDouble()
        if (moveX * moveX + moveZ * moveZ <= 1.0e-4) {
            return Vec3.ZERO
        }

        val yawRad = Math.toRadians((movementYaw ?: player.yRot).toDouble())
        val forwardX = -kotlin.math.sin(yawRad)
        val forwardZ = kotlin.math.cos(yawRad)
        val rightX = -forwardZ
        val rightZ = forwardX
        val worldX = forwardX * moveZ + rightX * moveX
        val worldZ = forwardZ * moveZ + rightZ * moveX
        return Vec3(worldX, 0.0, worldZ)
    }

    /** 本 tick 已发生的水平位移（`x-xOld` 与 `deltaMovement` 取较大者）。 */
    fun measuredHorizontalVelocity(player: ServerPlayer): Vec3 {
        val deltaMovement = Vec3Math.horizontal(player.deltaMovement)
        val positionDelta = Vec3(player.x - player.xOld, 0.0, player.z - player.zOld)
        return if (positionDelta.lengthSqr() > deltaMovement.lengthSqr()) positionDelta else deltaMovement
    }

    /**
     * 推球/碰撞用水平速度：在位移、`deltaMovement`、`getDeltaMovement`、移动意图换算速度中取模长最大者。
     * 足球实体 tick 常早于玩家位移写入，不能只看 `x-xOld`。
     */
    fun bestHorizontalVelocity(player: ServerPlayer): Vec3 {
        val candidates = listOf(
            measuredHorizontalVelocity(player),
            Vec3Math.horizontal(player.getDeltaMovement()),
            intendedHorizontalVelocity(player),
        )
        return candidates.maxByOrNull { it.lengthSqr() } ?: Vec3.ZERO
    }

    /**
     * 客户端上报的移动意图换算成的水平速度（blocks/tick）。
     * 与 [FootballDribbleAssist] 一致：在位移尚未写入或几何重叠时仍能反映「正在走」。
     */
    fun intendedHorizontalVelocity(player: ServerPlayer): Vec3 {
        val intent = Vec3Math.horizontal(player.lastClientMoveIntent)
        if (intent.lengthSqr() <= 1.0e-8) {
            return Vec3.ZERO
        }
        val speed = intendedHorizontalSpeed(player, intent.length().coerceIn(0.0, 1.0))
        return Vec3Math.normalizeSafe(intent).scale(speed)
    }

    fun intendedHorizontalSpeed(player: ServerPlayer, inputMagnitude: Double = 1.0): Double {
        val intent = Vec3Math.horizontal(player.lastClientMoveIntent)
        if (intent.lengthSqr() <= 1.0e-8) {
            return 0.0
        }
        val baseSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED)
        val sprintScale = if (player.isSprinting) 1.3 else 1.0
        return baseSpeed * sprintScale * inputMagnitude.coerceIn(0.0, 1.0)
    }

    /** @see bestHorizontalVelocity */
    fun pushHorizontalVelocity(player: ServerPlayer): Vec3 = bestHorizontalVelocity(player)

    private fun horizontalMoveIntent(intent: Vec3): Vec3 {
        return Vec3(intent.x, 0.0, intent.z)
    }

    /** 将世界水平移动意图从 [fromYaw] 的本地前后左右，换算到 [toYaw] 基准下的世界方向。 */
    private fun rebaseHorizontalMovement(world: Vec3, fromYaw: Float, toYaw: Float): Vec3 {
        val (localX, localZ) = decomposeHorizontalMovement(world, fromYaw)
        if (localX * localX + localZ * localZ <= 1.0e-4) {
            return Vec3.ZERO
        }
        return composeHorizontalMovement(localX, localZ, toYaw)
    }

    private fun decomposeHorizontalMovement(world: Vec3, yawDeg: Float): Pair<Double, Double> {
        val yawRad = Math.toRadians(yawDeg.toDouble())
        val forwardX = -kotlin.math.sin(yawRad)
        val forwardZ = kotlin.math.cos(yawRad)
        val rightX = -forwardZ
        val rightZ = forwardX
        val moveX = world.x * rightX + world.z * rightZ
        val moveZ = world.x * forwardX + world.z * forwardZ
        return moveX to moveZ
    }

    private fun composeHorizontalMovement(moveX: Double, moveZ: Double, yawDeg: Float): Vec3 {
        val yawRad = Math.toRadians(yawDeg.toDouble())
        val forwardX = -kotlin.math.sin(yawRad)
        val forwardZ = kotlin.math.cos(yawRad)
        val rightX = -forwardZ
        val rightZ = forwardX
        val worldX = forwardX * moveZ + rightX * moveX
        val worldZ = forwardZ * moveZ + rightZ * moveX
        return Vec3(worldX, 0.0, worldZ)
    }
}
