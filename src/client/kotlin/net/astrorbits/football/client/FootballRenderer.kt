package net.astrorbits.football.client

import com.mojang.blaze3d.vertex.PoseStack
import net.astrorbits.football.Football
import net.astrorbits.football.item.Items
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.item.ItemModelResolver
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

class FootballRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<Football, FootballRenderState>(context) {

    private val itemModelResolver: ItemModelResolver = context.itemModelResolver
    private val footballStack by lazy { ItemStack(Items.FOOTBALL) }

    init {
        shadowRadius = MODEL_Y_OFFSET
    }

    override fun createRenderState(): FootballRenderState = FootballRenderState()

    override fun extractRenderState(entity: Football, state: FootballRenderState, partialTick: Float) {
        super.extractRenderState(entity, state, partialTick)
        state.orientation.set(entity.getOrientation(partialTick))
        itemModelResolver.updateForNonLiving(
            state.item,
            footballStack,
            DISPLAY_CONTEXT,
            entity
        )
    }

    override fun submit(
        state: FootballRenderState,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        cameraState: CameraRenderState
    ) {
        if (!state.item.isEmpty) {
            poseStack.use {
                translate(0.0f, MODEL_Y_OFFSET, 0.0f)
                mulPose(state.orientation)
                state.item.submit(
                    poseStack,
                    collector,
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY,
                    state.outlineColor
                )
            }
        }

        super.submit(state, poseStack, collector, cameraState)
    }

    override fun getShadowRadius(state: FootballRenderState): Float = MODEL_Y_OFFSET

    companion object {
        private val DISPLAY_CONTEXT = ItemDisplayContext.GROUND
        private val MODEL_Y_OFFSET = FootballPhysicsConfig.RADIUS.toFloat()
    }
}
