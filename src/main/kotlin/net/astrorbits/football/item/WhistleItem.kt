package net.astrorbits.football.item

import net.astrorbits.football.FootballSounds
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
        FootballSounds.playWhistle(level, player.blockPosition(), player.random)
        player.cooldowns.addCooldown(stack, COOLDOWN_TICKS)
        return InteractionResult.SUCCESS_SERVER
    }

    companion object {
        private const val COOLDOWN_TICKS = 5
    }
}
