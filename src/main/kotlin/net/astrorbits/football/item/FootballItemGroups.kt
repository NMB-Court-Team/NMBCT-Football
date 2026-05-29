package net.astrorbits.football.item

import net.astrorbits.football.NMBCTFootball
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack

object FootballItemGroups {
    private val MAIN_GROUP = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
        .title(Component.translatable("itemGroup.nmbct-football.main"))
        .icon { ItemStack(Items.FOOTBALL) }
        .displayItems { _, output ->
            output.accept(Items.FOOTBALL)
            output.accept(Items.WHISTLE)
            output.accept(Items.RED_CARD)
            output.accept(Items.YELLOW_CARD)
        }
        .build()

    fun init() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, NMBCTFootball.id("main"), MAIN_GROUP)
    }
}
