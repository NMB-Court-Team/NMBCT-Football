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
            return horizontalMoveIntent(player.lastClientMoveIntent)
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
}
