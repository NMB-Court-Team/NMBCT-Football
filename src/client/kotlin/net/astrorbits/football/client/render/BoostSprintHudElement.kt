package net.astrorbits.football.client.render

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.client.StaminaClient
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import kotlin.math.roundToInt

/**
 * 加速疾跑：屏幕边缘紫色晕影（向外浓、向内透明）+ 体力条上方状态图标（透明度与 [StaminaClient.boostBlend] 同步）。
 */
class BoostSprintHudElement : HudElement {
    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        val blend = StaminaClient.boostBlend
        if (blend <= 0f && !StaminaClient.boostSprintActive) {
            return
        }

        val client = Minecraft.getInstance()
        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight

        val edge = (28 * blend).roundToInt().coerceAtLeast(0)
        if (edge > 0) {
            drawPurpleVignette(extra, width, height, edge, blend)
        }

        if (blend > 0f) {
            val displayW = (ICON_TEX_W * ICON_SCALE).roundToInt()
            val displayH = (ICON_TEX_H * ICON_SCALE).roundToInt()
            val iconX = 12
            val iconY = height - 72
            drawBoostSprintIcon(extra, iconX, iconY, blend)
        }
    }

    /**
     * 四边由外缘紫色平滑过渡到内侧全透明
     */
    private fun drawPurpleVignette(
        extra: GuiGraphicsExtractor,
        width: Int,
        height: Int,
        edge: Int,
        blend: Float,
    ) {
        val maxAlpha = (0x66 * blend).roundToInt().coerceIn(0, 0x66)
        if (maxAlpha <= 0) {
            return
        }
        val outer = purpleWithAlpha(maxAlpha)

        // 上 / 下：垂直线性渐变
        extra.fillGradient(0, 0, width, edge, outer, 0)
        extra.fillGradient(0, height - edge, width, height, 0, outer)

        // 左 / 右：水平渐变（按列插值，避免 fillGradient 仅支持纵向）；贯通全高，避免四角缺段
        for (i in 0 until edge) {
            val t = (i + 0.5f) / edge
            val fade = (1f - t).let { it * it }
            val alpha = (maxAlpha * fade).roundToInt().coerceIn(0, maxAlpha)
            if (alpha <= 0) {
                continue
            }
            val color = purpleWithAlpha(alpha)
            val x0 = i
            val x1 = i + 1
            extra.fill(x0, 0, x1, height, color)
            extra.fill(width - x1, 0, width - x0, height, color)
        }
    }

    private fun purpleWithAlpha(alpha: Int): Int = (alpha shl 24) or VIOLET_RGB

    private fun drawBoostSprintIcon(extra: GuiGraphicsExtractor, iconX: Int, iconY: Int, blend: Float) {
        val color = iconBlitColor(blend)
        if ((color ushr 24) == 0) {
            return
        }
        val displayW = ICON_TEX_W * ICON_SCALE
        val displayH = ICON_TEX_H * ICON_SCALE
        val centerX = iconX + displayW / 2f
        val centerY = iconY + displayH / 2f
        val pose = extra.pose()
        pose.pushMatrix()
        pose.translate(centerX + ICON_TRANSLATE_X, centerY + ICON_TRANSLATE_Y)
        pose.scale(ICON_SCALE, ICON_SCALE)
        pose.translate(-ICON_TEX_W / 2f, -ICON_TEX_H / 2f)
        extra.blit(
            RenderPipelines.GUI_TEXTURED,
            BOOST_SPRINT_ICON,
            0,
            0,
            0f,
            0f,
            ICON_TEX_W,
            ICON_TEX_H,
            ICON_TEX_W,
            ICON_TEX_H,
            color,
        )
        pose.popMatrix()
    }

    /** 与 [StaminaClient.boostBlend] 同步的 ARGB 透明度（体力条紫色渐变同一套 0→1 节奏）。 */
    private fun iconBlitColor(blend: Float): Int {
        val a = (blend.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        return (a shl 24) or 0x00FFFFFF
    }

    companion object {
        /** 纹理文件 [boost_sprint.png] 的像素宽高。 */
        const val ICON_TEX_W = 36
        const val ICON_TEX_H = 28

        /** 相对纹理中心的 GUI 缩放（1 = 36×28 像素绘制）。在 companion 中修改。 */
        const val ICON_SCALE = 1f

        /** 相对图标中心的平移（GUI 像素，正 X 向右、正 Y 向下）。同上文件 companion 修改。 */
        const val ICON_TRANSLATE_X = 0f
        const val ICON_TRANSLATE_Y = 0f

        private const val VIOLET_RGB = 0x9C27B0

        val BOOST_SPRINT_ICON: Identifier =
            Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "textures/gui/sprites/hud/boost_sprint.png")
    }
}
