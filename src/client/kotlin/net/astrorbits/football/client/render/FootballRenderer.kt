package net.astrorbits.football.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.astrorbits.football.Football
import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.client.util.use
import net.astrorbits.football.item.Items
import net.astrorbits.football.util.GoalkeeperHoldPoseUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.item.ItemModelResolver
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

/**
 * 足球渲染：近距离使用物品 3D 模型，远距离（> [BILLBOARD_DISTANCE]）改为始终面向相机的面片，
 * 降低远处大量三角面的开销与视觉噪点。
 *
 * 面片贴图路径见 [BILLBOARD_TEXTURE]（资源文件放在
 * `assets/nmbct-football/textures/entity/football_billboard.png`）。
 */
class FootballRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<Football, FootballRenderState>(context) {

    private val itemModelResolver: ItemModelResolver = context.itemModelResolver
    private val footballStack by lazy { ItemStack(Items.FOOTBALL) }

    init {
        shadowRadius = SHADOW_RADIUS
    }

    override fun createRenderState(): FootballRenderState = FootballRenderState()

    override fun extractRenderState(entity: Football, state: FootballRenderState, partialTick: Float) {
        super.extractRenderState(entity, state, partialTick)
        val holderEntityId = entity.getHolderEntityId()
        var firstPersonHold = false
        val renderPos = if (holderEntityId >= 0) {
            val level = entity.level()
            val holder = level.getEntity(holderEntityId)
            if (holder != null) {
                val camera = Minecraft.getInstance().cameraEntity
                firstPersonHold = camera != null &&
                    camera.id == holderEntityId &&
                    Minecraft.getInstance().options.cameraType.isFirstPerson
                if (firstPersonHold) {
                    GoalkeeperHoldPoseUtil.computeFirstPersonHoldPos(holder, partialTick)
                } else {
                    GoalkeeperHoldPoseUtil.computeBallEntityPosInterpolated(holder, partialTick)
                }
            } else {
                entity.getRenderPosition(partialTick)
            }
        } else {
            entity.getRenderPosition(partialTick)
        }
        state.x = renderPos.x
        state.y = renderPos.y
        state.z = renderPos.z
        state.orientation.set(
            if (holderEntityId >= 0) {
                val holder = entity.level().getEntity(holderEntityId)
                if (holder != null) {
                    GoalkeeperHoldPoseUtil.computeHeldOrientation(holder, partialTick, firstPersonHold)
                } else {
                    entity.getOrientation(partialTick)
                }
            } else {
                entity.getOrientation(partialTick)
            }
        )
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
        if (shouldUseBillboard(state, cameraState)) {
            submitBillboard(state, poseStack, collector)
        } else if (!state.item.isEmpty) {
            poseStack.use {
                translate(0f, MODEL_Y_OFFSET, 0f)
                scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE)
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

    private fun shouldUseBillboard(state: FootballRenderState, cameraState: CameraRenderState): Boolean {
        val centerY = state.y + MODEL_Y_OFFSET
        val dx = cameraState.pos.x - state.x
        val dy = cameraState.pos.y - centerY
        val dz = cameraState.pos.z - state.z
        return dx * dx + dy * dy + dz * dz > BILLBOARD_DISTANCE_SQ
    }

    private fun submitBillboard(
        state: FootballRenderState,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
    ) {
        val renderType = RenderTypes.entityTranslucent(BILLBOARD_TEXTURE)
        poseStack.use {
            translate(0f, MODEL_Y_OFFSET, 0f)
            // 与原版名牌/粒子一致：使用相机朝向，使面片始终正对玩家。
            val cameraOrientation = entityRenderDispatcher.camera?.rotation() ?: return
            mulPose(cameraOrientation)
            collector.submitCustomGeometry(poseStack, renderType) { pose, vc ->
                drawBillboardQuad(pose, vc, state.lightCoords)
            }
        }
    }

    private fun drawBillboardQuad(pose: PoseStack.Pose, vc: VertexConsumer, light: Int) {
        val half = BILLBOARD_HALF_SIZE
        val color = 0xFFFFFFFF.toInt()
        val nx = 0f
        val ny = 0f
        val nz = 1f

        vc.addVertex(pose, -half, -half, 0f)
            .setColor(color)
            .setUv(0f, 1f)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz)
        vc.addVertex(pose, half, -half, 0f)
            .setColor(color)
            .setUv(1f, 1f)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz)
        vc.addVertex(pose, half, half, 0f)
            .setColor(color)
            .setUv(1f, 0f)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz)
        vc.addVertex(pose, -half, half, 0f)
            .setColor(color)
            .setUv(0f, 0f)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz)
    }

    override fun getShadowRadius(state: FootballRenderState): Float = SHADOW_RADIUS

    companion object {
        private val DISPLAY_CONTEXT = ItemDisplayContext.NONE
        private const val SHADOW_RADIUS = 0.35f
        private const val MODEL_Y_OFFSET = 0.375f
        private const val MODEL_SCALE = 0.7f

        /** 超过该距离（格）时改用面向相机的面片渲染。 */
        private const val BILLBOARD_DISTANCE = 80.0
        private const val BILLBOARD_DISTANCE_SQ = BILLBOARD_DISTANCE * BILLBOARD_DISTANCE

        /** 面片半宽/半高（方块），与近处模型视觉大小大致相当。 */
        private const val BILLBOARD_HALF_SIZE = 0.42f

        /**
         * 远距离面片贴图。请将 PNG 放在：
         * `src/main/resources/assets/nmbct-football/textures/entity/football_billboard.png`
         * 建议尺寸 128×128，带透明通道，正交视角的足球图标即可。
         */
        private val BILLBOARD_TEXTURE: Identifier = NMBCTFootball.id("textures/entity/football_billboard.png")
    }
}
