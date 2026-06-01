package net.astrorbits.football.client

import net.astrorbits.football.client.key.FootballInputHandler
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.item.Items
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback

/**
 * 手持足球物品时，左键优先触发 ITEM_THROW 发包，并取消本地原版攻击/破坏流程。
 */
object FootballClientAttackInteractions {
    fun register() {
        ClientPreAttackCallback.EVENT.register(ClientPreAttackCallback { client, player, _ ->
            if (player.mainHandItem.isEmpty || !player.mainHandItem.`is`(Items.FOOTBALL)) {
                return@ClientPreAttackCallback false
            }
            if (client.screen != null || client.isPaused) {
                return@ClientPreAttackCallback true
            }
            if (player.cooldowns.isOnCooldown(player.mainHandItem)) {
                return@ClientPreAttackCallback true
            }
            if (MatchStartClient.isLocked) {
                return@ClientPreAttackCallback true
            }
            FootballInputHandler.sendItemThrow(player)
            true
        })
    }
}
