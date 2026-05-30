package net.astrorbits.football.input

import net.minecraft.server.level.ServerPlayer
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
