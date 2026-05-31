package net.astrorbits.football.block

import net.astrorbits.football.NMBCTFootball
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour

object Blocks {

    val GOAL_NET_ANCHOR = registerWithItem(
        "goal_net_anchor",
        BlockBehaviour.Properties.of()
            .strength(0.6f)
            .noOcclusion(),
        factory = ::GoalNetAnchorBlock,
    )

    fun init() {
        // static init
    }

    fun <B : Block> register(
        id: String,
        properties: BlockBehaviour.Properties,
        factory: (BlockBehaviour.Properties) -> B,
    ): B {
        val key = ResourceKey.create(Registries.BLOCK, NMBCTFootball.id(id))
        return Registry.register(BuiltInRegistries.BLOCK, key, factory(properties.setId(key)))
    }

    fun <B : Block> registerWithItem(
        id: String,
        properties: BlockBehaviour.Properties,
        itemProperties: Item.Properties = Item.Properties(),
        factory: (BlockBehaviour.Properties) -> B,
    ): B {
        val block = register(id, properties, factory)
        registerBlockItem(id, block, itemProperties)
        return block
    }

    private fun registerBlockItem(id: String, block: Block, properties: Item.Properties) {
        val key = ResourceKey.create(Registries.ITEM, NMBCTFootball.id(id))
        Registry.register(BuiltInRegistries.ITEM, key, BlockItem(block, properties.setId(key)))
    }
}
