package net.astrorbits.football.client.item

import net.astrorbits.football.item.Items
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

object GoalNetConnectorItemTooltipClient {
    fun register() {
        ItemTooltipCallback.EVENT.register { stack, _, _, tooltip ->
            if (!stack.`is`(Items.GOAL_NET_CONNECTOR)) return@register
            tooltip.add(
                Component.translatable("item.nmbct-football.goal_net_connector.tooltip.1")
                    .withStyle(ChatFormatting.GRAY)
            )
            tooltip.add(
                Component.translatable("item.nmbct-football.goal_net_connector.tooltip.2")
                    .withStyle(ChatFormatting.GRAY)
            )
        }
    }
}
