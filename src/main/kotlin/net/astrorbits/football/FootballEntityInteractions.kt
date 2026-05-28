package net.astrorbits.football

import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult

/**
 * 实体右键交互：26.1 中 [Football.interact] 往往不会被调用，
 * 因此在 Fabric 收包层通过 [UseEntityCallback] 处理。
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
            if (result != InteractionResult.PASS) {
                return@register result
            }
            InteractionResult.PASS
        }
    }
}
