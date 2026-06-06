package net.astrorbits.football.util

import net.astrorbits.football.GoalNetEntity
import net.astrorbits.football.block.GoalNetAnchorBlock
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import java.util.*

/**
 * 维护「锚点方块 ↔ 球网实体」的双向索引，供锚点破坏时销毁关联网。
 */
object GoalNetAnchorLinks {
    private data class AnchorKey(val dimension: ResourceKey<Level>, val pos: BlockPos)

    private val anchorToNets = HashMap<AnchorKey, MutableSet<UUID>>()
    private val netToAnchors = HashMap<UUID, List<BlockPos>>()

    /**
     * 玩家破坏锚点时记录破坏者；实际销毁在 [PlayerBlockBreakEvents.AFTER] 或 [GoalNetAnchorBlock.destroy] 中执行。
     * 专用服务器上玩家破坏方块通常不会调用 [net.minecraft.world.level.block.Block.destroy]（仅客户端效果），
     * 因此必须以 AFTER 作为服务端权威路径。
     */
    private val pendingBreakers = HashMap<AnchorKey, Player>()

    fun registerEvents() {
        PlayerBlockBreakEvents.BEFORE.register { world, player, pos, state, _ ->
            if (!world.isClientSide && state.block is GoalNetAnchorBlock) {
                pendingBreakers[AnchorKey(world.dimension(), pos)] = player
            }
            true
        }
        PlayerBlockBreakEvents.AFTER.register { world, _, pos, state, _ ->
            if (!world.isClientSide && state.block is GoalNetAnchorBlock) {
                onAnchorRemoved(world, pos)
            }
        }
    }

    fun takePendingBreaker(level: Level, pos: BlockPos): Player? =
        pendingBreakers.remove(AnchorKey(level.dimension(), pos))

    fun register(net: GoalNetEntity, anchorBlocks: List<BlockPos>) {
        if (levelIsClient(net)) return
        unregister(net)
        val dimension = net.level().dimension()
        val anchors = anchorBlocks.toList()
        netToAnchors[net.uuid] = anchors
        for (pos in anchors) {
            anchorToNets.getOrPut(AnchorKey(dimension, pos)) { HashSet() }.add(net.uuid)
        }
    }

    fun unregister(net: GoalNetEntity) {
        if (levelIsClient(net)) return
        val anchors = netToAnchors.remove(net.uuid) ?: return
        val dimension = net.level().dimension()
        for (pos in anchors) {
            val key = AnchorKey(dimension, pos)
            val nets = anchorToNets[key] ?: continue
            nets.remove(net.uuid)
            if (nets.isEmpty()) {
                anchorToNets.remove(key)
            }
        }
    }

    /** 锚点方块被移除（破坏、替换、活塞推动等）时调用。 */
    fun onAnchorRemoved(level: Level, pos: BlockPos, breaker: Player? = takePendingBreaker(level, pos)) {
        if (level.isClientSide) return
        val key = AnchorKey(level.dimension(), pos)
        val netIds = anchorToNets[key]?.toList() ?: return
        if (netIds.isEmpty()) return
        val serverLevel = level as? ServerLevel ?: return
        for (netId in netIds) {
            val entity = serverLevel.getEntity(netId) as? GoalNetEntity
            if (entity != null) {
                entity.discardFromAnchorBreak(breaker)
            } else {
                unregisterById(serverLevel, netId)
            }
        }
    }

    private fun unregisterById(level: ServerLevel, netId: UUID) {
        val anchors = netToAnchors.remove(netId) ?: return
        val dimension = level.dimension()
        for (anchorPos in anchors) {
            val anchorKey = AnchorKey(dimension, anchorPos)
            val nets = anchorToNets[anchorKey] ?: continue
            nets.remove(netId)
            if (nets.isEmpty()) {
                anchorToNets.remove(anchorKey)
            }
        }
    }

    private fun levelIsClient(net: GoalNetEntity): Boolean = net.level().isClientSide
}
