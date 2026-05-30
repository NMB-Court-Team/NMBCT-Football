package net.astrorbits.football.client.key

import com.mojang.blaze3d.platform.InputConstants
import net.astrorbits.football.NMBCTFootball
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW

object FootballKeyBindings {
    val CATEGORY: KeyMapping.Category = KeyMapping.Category.register(NMBCTFootball.id("football"))

    val KICK: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping(
            "key.nmbct-football.kick",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY
        )
    )

    val TRAP: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping(
            "key.nmbct-football.trap",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            CATEGORY
        )
    )

    val CHIP: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping(
            "key.nmbct-football.chip",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
        )
    )

    val DRIBBLE: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping(
            "key.nmbct-football.dribble",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_SPACE,
            CATEGORY
        )
    )

    fun init() {
        // static init
    }
}
