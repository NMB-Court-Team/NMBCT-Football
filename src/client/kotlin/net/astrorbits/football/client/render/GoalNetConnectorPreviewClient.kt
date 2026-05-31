package net.astrorbits.football.client.render

import net.astrorbits.football.block.GoalNetAnchorBlock
import net.astrorbits.football.item.GoalNetConnectorItem
import net.astrorbits.football.item.Items
import net.astrorbits.football.network.GoalNetConnectorSelectionS2CPayload
import net.astrorbits.football.util.GoalNetGeometry
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

/**
 * 客户端：显示球网连接器已选锚点（黄）与选点顺序连线（绿）。
 * 准星指向球网实体时不显示。
 */
object GoalNetConnectorPreviewClient {
    private var selectedBlocks: List<BlockPos> = emptyList()

    private val yellowDust = DustParticleOptions(0xFFFFFF00.toInt(), 0.85f)
    private val greenDust = DustParticleOptions(0xFF00FF00.toInt(), 0.75f)

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(GoalNetConnectorSelectionS2CPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                selectedBlocks = payload.anchorBlocks
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player ?: return@register
            val level = client.level ?: return@register
            if (!isHoldingConnector(player)) return@register
            if (selectedBlocks.isEmpty()) return@register
            if (GoalNetConnectorItem.raycastGoalNet(player, level) != null) return@register

            val anchorPositions = GoalNetGeometry.resolveAnchorPositions(level, selectedBlocks) ?: return@register
            renderPoints(level, anchorPositions)
            renderEdges(anchorPositions)
        }
    }

    private fun isHoldingConnector(player: Player): Boolean =
        player.mainHandItem.`is`(Items.GOAL_NET_CONNECTOR) ||
            player.offhandItem.`is`(Items.GOAL_NET_CONNECTOR)

    private fun renderPoints(level: net.minecraft.client.multiplayer.ClientLevel, points: List<Vec3>) {
        for (pos in points) {
            level.addParticle(
                yellowDust,
                pos.x,
                pos.y,
                pos.z,
                0.02,
                0.02,
                0.02,
            )
        }
    }

    private fun renderEdges(points: List<Vec3>) {
        if (points.size < 2) return
        val level = Minecraft.getInstance().level ?: return
        for (i in 0 until points.size - 1) {
            spawnLineParticles(level, points[i], points[i + 1])
        }
    }

    private fun spawnLineParticles(
        level: net.minecraft.client.multiplayer.ClientLevel,
        from: Vec3,
        to: Vec3,
    ) {
        val dist = from.distanceTo(to)
        val steps = kotlin.math.max(2, (dist * 8.0).toInt())
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val p = from.lerp(to, t)
            level.addParticle(
                greenDust,
                p.x,
                p.y,
                p.z,
                0.0,
                0.0,
                0.0,
            )
        }
    }
}
