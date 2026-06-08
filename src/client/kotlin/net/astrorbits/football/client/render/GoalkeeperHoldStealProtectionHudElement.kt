package net.astrorbits.football.client.render

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/** 守门员持球抢球保护期间，在十字准心下方显示提示。 */
class GoalkeeperHoldStealProtectionHudElement : HudElement {
    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        val client = Minecraft.getInstance()
        if (client.screen != null || client.isPaused) return
        val player = client.player ?: return
        val level = client.level ?: return

        val content = FootballHudHintResolver.resolve(
            FootballHudHintResolver.Position.CROSSHAIR_NEAR,
            player,
            level,
            delta,
        ) as? FootballHudHintResolver.Content.TextLines ?: return

        val line = content.lines.firstOrNull() ?: return
        drawCrosshairText(extra, client, line.format(), line.color)
    }

    companion object {
        private const val TEXT_SCALE = 1.0f

        fun drawCrosshairText(extra: GuiGraphicsExtractor, client: Minecraft, label: String, color: Int) {
            val font = client.font
            val width = client.window.guiScaledWidth
            val height = client.window.guiScaledHeight
            val cx = width / 2f
            val baseY = height / 2f + FootballHudHintResolver.Layout.CROSSHAIR_NEAR_OFFSET

            val pose = extra.pose()
            pose.pushMatrix()
            pose.translate(cx, baseY)
            pose.scale(TEXT_SCALE, TEXT_SCALE)
            val textW = font.width(label)
            extra.text(font, label, (-textW / 2f).toInt(), 0, color, true)
            pose.popMatrix()
        }
    }
}
