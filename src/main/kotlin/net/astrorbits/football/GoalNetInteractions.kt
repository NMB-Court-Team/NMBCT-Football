package net.astrorbits.football

import net.astrorbits.football.entity.GoalNetEntity
import net.astrorbits.football.item.GoalNetConnectorItem
import net.astrorbits.football.item.GoalNetConnectorSounds
import net.astrorbits.football.item.Items
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult

/**
 * 手持球网连接器时对球网实体的交互：
 * - 左键：仅连接器可攻击并销毁
 * - 右键：委托 [GoalNetConnectorItem.handleUse]（方块射线优先于实体）
 */
object GoalNetInteractions {
    fun register() {
        AttackEntityCallback.EVENT.register { player, level, hand, entity, _ ->
            if (level.isClientSide) return@register InteractionResult.PASS
            if (entity !is GoalNetEntity || player !is ServerPlayer) return@register InteractionResult.PASS
            if (!player.getItemInHand(hand).`is`(Items.GOAL_NET_CONNECTOR)) {
                return@register InteractionResult.PASS
            }
            entity.discard()
            GoalNetConnectorSounds.playNetDestroy(player, entity)
            player.sendOverlayMessage(Component.translatable("message.nmbct-football.goal_net.destroyed"))
            InteractionResult.SUCCESS
        }

        UseEntityCallback.EVENT.register { player, level, hand, entity, _ ->
            if (level.isClientSide) return@register InteractionResult.PASS
            if (entity !is GoalNetEntity || player !is ServerPlayer) return@register InteractionResult.PASS
            if (!player.getItemInHand(hand).`is`(Items.GOAL_NET_CONNECTOR)) return@register InteractionResult.PASS
            val item = player.getItemInHand(hand).item as GoalNetConnectorItem
            val result = item.handleUse(level, player)
            if (result == InteractionResult.PASS) InteractionResult.PASS else InteractionResult.SUCCESS
        }
    }
}
