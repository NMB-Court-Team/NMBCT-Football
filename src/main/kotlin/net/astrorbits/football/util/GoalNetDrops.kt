package net.astrorbits.football.util

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.entity.GoalNetEntity
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3

object GoalNetDrops {
    const val CONNECTOR_DESTROY_STRING_RETURN = 8

    private val ANCHOR_BREAK_LOOT_TABLE: ResourceKey<net.minecraft.world.level.storage.loot.LootTable> =
        ResourceKey.create(Registries.LOOT_TABLE, NMBCTFootball.id("entities/goal_net_anchor_break"))

    /** 生存/冒险规则下掉落或返还线；创造模式玩家不触发。 */
    fun appliesSurvivalRewards(player: Player?): Boolean {
        if (player == null) return true
        return !player.hasInfiniteMaterials()
    }

    fun dropAnchorBreakLoot(level: ServerLevel, net: GoalNetEntity, breaker: Player?) {
        if (!appliesSurvivalRewards(breaker)) return
        val lootTable = level.server.reloadableRegistries().getLootTable(ANCHOR_BREAK_LOOT_TABLE)
        val dropPos = net.dropCenter()
        val damageSource = if (breaker is ServerPlayer) {
            level.damageSources().playerAttack(breaker)
        } else {
            level.damageSources().generic()
        }
        val params = LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, dropPos)
            .withParameter(LootContextParams.THIS_ENTITY, net)
            .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
            .create(LootContextParamSets.ENTITY)
        for (stack in lootTable.getRandomItems(params)) {
            Block.popResource(level, BlockPos.containing(dropPos), stack)
        }
    }

    fun returnConnectorDestroyString(player: ServerPlayer) {
        if (!appliesSurvivalRewards(player)) return
        val stack = ItemStack(Items.STRING, CONNECTOR_DESTROY_STRING_RETURN)
        if (!player.inventory.add(stack)) {
            player.drop(stack, false)
        }
    }

    fun canDestroyWithConnector(player: ServerPlayer): Boolean =
        player.gameMode.gameModeForPlayer != GameType.ADVENTURE
}

private fun GoalNetEntity.dropCenter(): Vec3 = boundingBox.center
