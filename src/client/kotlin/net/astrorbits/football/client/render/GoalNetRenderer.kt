package net.astrorbits.football.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.astrorbits.football.entity.GoalNetEntity
import net.astrorbits.football.physics.GoalNetConfig
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState

/**
 * 球网渲染：把节点网格的结构边渲染成朝向相机的细长四边形。
 *
 * 采用固定世界宽度（叠加少量随距离增益），因此线条粗细随玩家与线之间的距离变化，
 * 区别于原版固定屏幕像素宽度的线段渲染。
 */
class GoalNetRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<GoalNetEntity, GoalNetRenderState>(context) {

    init {
        shadowRadius = 0.0f
    }

    override fun createRenderState(): GoalNetRenderState = GoalNetRenderState()

    override fun extractRenderState(entity: GoalNetEntity, state: GoalNetRenderState, partialTick: Float) {
        super.extractRenderState(entity, state, partialTick)
        state.cols = entity.clientCols
        state.rows = entity.clientRows
        state.relative = entity.clientRelative
        // 实体不移动，使用其当前世界坐标作为局部原点。
        state.x = entity.x
        state.y = entity.y
        state.z = entity.z
    }

    override fun submit(
        state: GoalNetRenderState,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        cameraState: CameraRenderState
    ) {
        val rel = state.relative
        if (rel != null && state.cols >= 2 && state.rows >= 2 && rel.size >= state.cols * state.rows * 3) {
            // 相机在“实体局部空间”中的位置（局部原点 = 实体世界坐标）。
            val camLocalX = (cameraState.pos.x - state.x).toFloat()
            val camLocalY = (cameraState.pos.y - state.y).toFloat()
            val camLocalZ = (cameraState.pos.z - state.z).toFloat()

            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads()) { pose, vc ->
                drawNet(pose, vc, state, rel, camLocalX, camLocalY, camLocalZ)
            }
        }
        super.submit(state, poseStack, collector, cameraState)
    }

    private fun drawNet(
        pose: PoseStack.Pose,
        vc: VertexConsumer,
        state: GoalNetRenderState,
        rel: FloatArray,
        camX: Float,
        camY: Float,
        camZ: Float,
    ) {
        val cols = state.cols
        val rows = state.rows
        for (j in 0 until rows) {
            for (i in 0 until cols) {
                val a = (j * cols + i) * 3
                if (i + 1 < cols) {
                    val b = (j * cols + (i + 1)) * 3
                    edgeQuad(pose, vc, rel, a, b, camX, camY, camZ)
                }
                if (j + 1 < rows) {
                    val b = ((j + 1) * cols + i) * 3
                    edgeQuad(pose, vc, rel, a, b, camX, camY, camZ)
                }
            }
        }
    }

    private fun edgeQuad(
        pose: PoseStack.Pose,
        vc: VertexConsumer,
        rel: FloatArray,
        a: Int,
        b: Int,
        camX: Float,
        camY: Float,
        camZ: Float,
    ) {
        val ax = rel[a]; val ay = rel[a + 1]; val az = rel[a + 2]
        val bx = rel[b]; val by = rel[b + 1]; val bz = rel[b + 2]

        // 边方向
        var ex = bx - ax; var ey = by - ay; var ez = bz - az
        val eLen = Math.sqrt((ex * ex + ey * ey + ez * ez).toDouble()).toFloat()
        if (eLen < 1.0e-5f) return
        ex /= eLen; ey /= eLen; ez /= eLen

        // 中点到相机方向
        val mx = (ax + bx) * 0.5f; val my = (ay + by) * 0.5f; val mz = (az + bz) * 0.5f
        var cx = camX - mx; var cy = camY - my; var cz = camZ - mz
        val cLen = Math.sqrt((cx * cx + cy * cy + cz * cz).toDouble()).toFloat()
        if (cLen < 1.0e-5f) return
        cx /= cLen; cy /= cLen; cz /= cLen

        // 侧向 = edge × toCam（朝向相机的横向偏移方向）
        var sx = ey * cz - ez * cy
        var sy = ez * cx - ex * cz
        var sz = ex * cy - ey * cx
        val sLen = Math.sqrt((sx * sx + sy * sy + sz * sz).toDouble()).toFloat()
        if (sLen < 1.0e-5f) return
        sx /= sLen; sy /= sLen; sz /= sLen

        val halfWidth = (GoalNetConfig.LINE_HALF_WIDTH + cLen * GoalNetConfig.LINE_WIDTH_DISTANCE_GAIN).toFloat()
        sx *= halfWidth; sy *= halfWidth; sz *= halfWidth

        val color = GoalNetConfig.LINE_COLOR_ARGB
        vc.addVertex(pose, ax - sx, ay - sy, az - sz).setColor(color)
        vc.addVertex(pose, bx - sx, by - sy, bz - sz).setColor(color)
        vc.addVertex(pose, bx + sx, by + sy, bz + sz).setColor(color)
        vc.addVertex(pose, ax + sx, ay + sy, az + sz).setColor(color)
    }

    override fun getShadowRadius(state: GoalNetRenderState): Float = 0.0f
}
