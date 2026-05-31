package net.astrorbits.football.client

import net.astrorbits.football.input.SlideTackleStateAccess
import net.astrorbits.football.network.SlideTackleStateS2CPayload
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.player.Player
import kotlin.math.atan2

object SlideTackleStateClient {
    private val slidingEntities = mutableSetOf<Int>()
    private const val MIN_FACING_SPEED_SQR = 1.0e-4

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(SlideTackleStateS2CPayload.TYPE) { payload, _ ->
            val level = Minecraft.getInstance().level ?: return@registerGlobalReceiver
            if (payload.sliding) {
                slidingEntities.add(payload.entityId)
            } else {
                slidingEntities.remove(payload.entityId)
            }
            applySlideStateToEntity(level.getEntity(payload.entityId), payload.sliding)
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val level = client.level
            if (level == null) {
                slidingEntities.clear()
                return@register
            }
            // 只做实体生命周期清理；朝向与结束状态都由服务端滑铲状态驱动。
            slidingEntities.removeIf { entityId ->
                val entity = level.getEntity(entityId)
                if (entity == null) {
                    return@removeIf true
                }
                alignSlideFacing(entity as? Player, client.player)
                false
            }
        }
    }

    @JvmStatic
    fun isSliding(entityId: Int): Boolean = slidingEntities.contains(entityId)

    private fun applySlideStateToEntity(entity: net.minecraft.world.entity.Entity?, sliding: Boolean) {
        val player = entity as? Player ?: return
        (player as? SlideTackleStateAccess)?.nmbctFootball_setSlideTackling(sliding)
        player.refreshDimensions()
    }

    private fun alignSlideFacing(player: Player?, localPlayer: LocalPlayer?) {
        if (player == null || !isSliding(player.id)) {
            return
        }
        val velocity = player.deltaMovement
        val horizontalSpeedSqr = velocity.x * velocity.x + velocity.z * velocity.z
        if (horizontalSpeedSqr < MIN_FACING_SPEED_SQR) {
            return
        }
        val yaw = Math.toDegrees(atan2(-velocity.x, velocity.z)).toFloat()
        player.yBodyRot = yaw
        player.yBodyRotO = yaw
        player.yHeadRot = yaw
        player.yHeadRotO = yaw
        if (player !== localPlayer) {
            // 非本地玩家同时锁定实体 yaw，避免远端观察时出现姿态翻转。
            player.yRot = yaw
            player.yRotO = yaw
        }
    }
}
