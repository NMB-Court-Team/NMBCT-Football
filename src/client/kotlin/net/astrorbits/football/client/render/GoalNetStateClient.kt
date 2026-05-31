package net.astrorbits.football.client.render

import net.astrorbits.football.entity.GoalNetEntity
import net.astrorbits.football.network.GoalNetStateS2CPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

/**
 * 客户端：接收球网节点形变并写入对应实体，供 [GoalNetRenderer] 渲染。
 */
object GoalNetStateClient {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(GoalNetStateS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                val level = Minecraft.getInstance().level ?: return@execute
                val entity = level.getEntity(payload.entityId) as? GoalNetEntity ?: return@execute
                entity.applyClientState(payload.cols, payload.rows, payload.relativePositions)
            }
        }
    }
}
