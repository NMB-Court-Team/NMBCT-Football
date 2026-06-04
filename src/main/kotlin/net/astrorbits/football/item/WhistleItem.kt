package net.astrorbits.football.item

import net.astrorbits.football.network.FootballNetworking
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level

class WhistleItem(properties: Properties) : Item(properties) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        val stack = player.getItemInHand(hand)
        if (player.cooldowns.isOnCooldown(stack)) {
            return InteractionResult.PASS
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }
        FootballNetworking.syncWhistleUse(player as ServerPlayer)
        player.cooldowns.addCooldown(stack, COOLDOWN_TICKS)
        return InteractionResult.SUCCESS_SERVER
    }

    companion object {
        private const val COOLDOWN_TICKS = 5
    }
}
