package net.astrorbits.football.client

import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.astrorbits.football.Football

/**
 * Minimal renderer placeholder. The football is represented by its shadow and hitbox until a dedicated model is added.
 */
class FootballRenderer(context: EntityRendererProvider.Context) : EntityRenderer<Football, EntityRenderState>(context) {
    override fun createRenderState(): EntityRenderState = EntityRenderState()

    override fun extractRenderState(entity: Football, state: EntityRenderState, partialTick: Float) {
        super.extractRenderState(entity, state, partialTick)
    }
}
