package net.astrorbits.football.item

import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items as VanillaItems

/**
 * 生存/冒险模式下创建球网时消耗：连接器 1 点耐久 + 8 根线（任意颜色羊毛 1 个计 4 根线）。
 */
object GoalNetMaterialCost {
    const val MAX_DURABILITY = 127
    const val STRING_REQUIRED = 8
    const val WOOL_STRING_VALUE = 4

    fun requiresCost(player: Player): Boolean = !player.hasInfiniteMaterials()

    fun countStringValue(player: Player): Int {
        var total = 0
        for (i in 0 until player.inventory.containerSize) {
            total += stringValueOf(player.inventory.getItem(i))
        }
        return total
    }

    private fun stringValueOf(stack: ItemStack): Int {
        if (stack.isEmpty) return 0
        return when {
            stack.`is`(VanillaItems.STRING) -> stack.count
            stack.`is`(ItemTags.WOOL) -> stack.count * WOOL_STRING_VALUE
            else -> 0
        }
    }

    fun hasEnoughString(player: Player): Boolean =
        countStringValue(player) >= STRING_REQUIRED

    fun connectorHasDurability(stack: ItemStack): Boolean {
        if (!stack.`is`(Items.GOAL_NET_CONNECTOR)) return false
        if (!stack.isDamageableItem) return true
        return stack.damageValue < stack.maxDamage
    }

    fun findConnectorHand(player: Player, preferred: InteractionHand): InteractionHand? {
        if (player.getItemInHand(preferred).`is`(Items.GOAL_NET_CONNECTOR)) return preferred
        val other = if (preferred == InteractionHand.MAIN_HAND) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
        return if (player.getItemInHand(other).`is`(Items.GOAL_NET_CONNECTOR)) other else null
    }

    fun tryConsumeString(player: Player): Boolean {
        if (!hasEnoughString(player)) return false
        var remaining = STRING_REQUIRED
        for (i in 0 until player.inventory.containerSize) {
            if (remaining <= 0) break
            val stack = player.inventory.getItem(i)
            if (!stack.`is`(VanillaItems.STRING)) continue
            val take = minOf(stack.count, remaining)
            stack.shrink(take)
            remaining -= take
        }
        for (i in 0 until player.inventory.containerSize) {
            if (remaining <= 0) break
            val stack = player.inventory.getItem(i)
            if (!stack.`is`(ItemTags.WOOL)) continue
            while (remaining > 0 && !stack.isEmpty) {
                stack.shrink(1)
                remaining -= WOOL_STRING_VALUE
            }
        }
        return remaining <= 0
    }

    fun damageConnector(player: Player, hand: InteractionHand) {
        val stack = player.getItemInHand(hand)
        if (!stack.`is`(Items.GOAL_NET_CONNECTOR)) return
        val slot = if (hand == InteractionHand.MAIN_HAND) EquipmentSlot.MAINHAND else EquipmentSlot.OFFHAND
        stack.hurtAndBreak(1, player, slot)
    }
}
