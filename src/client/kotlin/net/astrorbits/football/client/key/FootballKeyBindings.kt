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
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_LEFT,
            CATEGORY
        )
    )

    val LOOK_AROUND: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping(
            "key.nmbct-football.look_around",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            CATEGORY
        )
    )

    val SLIDE_TACKLE: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping(
            "key.nmbct-football.slide_tackle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY
        )
    )

    val BOOST_SPRINT: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping(
            "key.nmbct-football.boost_sprint",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY
        )
    )

    val GK_DIVE: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping(
            "key.nmbct-football.gk_dive",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            CATEGORY
        )
    )

    val GK_CATCH: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping(
            "key.nmbct-football.gk_catch",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
        )
    )

    val INTERRUPT_CHARGE: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping(
            "key.nmbct-football.interrupt_charge",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            CATEGORY
        )
    )

    val OPEN_CLIENT_CONFIG: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping(
            "key.nmbct-football.open_client_config",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.value,
            CATEGORY
        )
    )

    fun init() {
        // static init
    }
}
