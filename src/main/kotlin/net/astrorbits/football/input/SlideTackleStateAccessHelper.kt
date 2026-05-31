package net.astrorbits.football.input

import net.astrorbits.football.mixinhelper.SlideTackleStateAccess
import net.minecraft.world.entity.player.Player

var Player.isSlideTackling: Boolean
    get() = (this as? SlideTackleStateAccess)?.`nmbctFootball$isSlideTackling`() ?: false
    set(value) {
        (this as? SlideTackleStateAccess)?.`nmbctFootball$setSlideTackling`(value)
    }
