package net.astrorbits.football

import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.Permissions
import net.minecraft.world.InteractionResult

/**
 * Shift右键捡球
 */
object FootballEntityInteractions {
    fun register() {
        UseEntityCallback.EVENT.register { player, level, _, entity, _ ->
            if (level.isClientSide) {
                return@register InteractionResult.PASS
            }
            if (entity !is Football || player !is ServerPlayer) {
                return@register InteractionResult.PASS
            }
            val result = entity.handlePlayerInteract(player)
            return@register result
        }
    }
}
