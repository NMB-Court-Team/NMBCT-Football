package net.astrorbits.football.item

import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult

/**
 * 持足球物品时，屏蔽原版左键破坏/攻击交互。
 * 左键动作由客户端发包（ITEM_THROW）并在服务端执行抛球。
 */
object FootballItemUseGuards {
    fun register() {
        AttackBlockCallback.EVENT.register { player, _, hand, _, _ ->
            if (hand != InteractionHand.MAIN_HAND) {
                return@register InteractionResult.PASS
            }
            if (!player.mainHandItem.`is`(Items.FOOTBALL)) {
                return@register InteractionResult.PASS
            }
            InteractionResult.FAIL
        }

        AttackEntityCallback.EVENT.register { player, _, hand, _, _ ->
            if (hand != InteractionHand.MAIN_HAND) {
                return@register InteractionResult.PASS
            }
            if (!player.mainHandItem.`is`(Items.FOOTBALL)) {
                return@register InteractionResult.PASS
            }
            InteractionResult.FAIL
        }
    }
}
