package net.astrorbits.football.client

import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.item.ItemStackRenderState
import org.joml.Quaternionf

class FootballRenderState : EntityRenderState() {
    val item = ItemStackRenderState()
    val orientation = Quaternionf()
}
