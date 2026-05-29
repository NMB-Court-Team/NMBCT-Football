package net.astrorbits.football.item

import net.astrorbits.football.NMBCTFootball
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity

object Items {
    val FOOTBALL = register(
        "football",
        Item.Properties()
            .rarity(Rarity.RARE),
        ::FootballItem
    )
    val WHISTLE = register(
        "whistle",
        Item.Properties().stacksTo(1),
        ::Item,
    )
    val RED_CARD = register(
        "red_card",
        Item.Properties(),
        ::Item,
    )
    val YELLOW_CARD = register(
        "yellow_card",
        Item.Properties(),
        ::Item,
    )

    fun init() {
        // static init
    }

    fun <I : Item> register(id: String, properties: Item.Properties, factory: (Item.Properties) -> I): I {
        val key = ResourceKey.create(Registries.ITEM, NMBCTFootball.id(id))
        return Registry.register(BuiltInRegistries.ITEM, key, factory(properties.setId(key)))
    }
}