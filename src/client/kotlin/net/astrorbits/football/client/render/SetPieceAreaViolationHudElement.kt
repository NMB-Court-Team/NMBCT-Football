package net.astrorbits.football.client.render

import net.astrorbits.football.client.SetPieceAreaViolationClient
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/** 区域违规警告：准心下方红色粗体（与抢球保护 HUD 错开）。 */
class SetPieceAreaViolationHudElement : HudElement {
    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        val client = Minecraft.getInstance()
        if (client.screen != null || client.isPaused || !SetPieceAreaViolationClient.isActive()) {
            return
        }

        val areaName = Component.translatable(SetPieceAreaViolationClient.areaNameKey).string
        val label = Component.translatable(
            "hud.nmbct-football.area_violation.warning",
            areaName,
            SetPieceAreaViolationClient.secondsRemaining,
        ).string

        val font = client.font
        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        val cx = width / 2f
        val baseY = height / 2f + CROSSHAIR_BELOW_OFFSET

        val pose = extra.pose()
        pose.pushMatrix()
        pose.translate(cx, baseY)
        pose.scale(TEXT_SCALE, TEXT_SCALE)
        val textW = font.width(label)
        extra.text(font, label, (-textW / 2f).toInt(), 0, LABEL_COLOR, true)
        pose.popMatrix()
    }

    companion object {
        private const val LABEL_COLOR = 0xFFFF4444.toInt()
        private const val TEXT_SCALE = 1.0f
        /** 在抢球保护提示下方，留出约一行字高的间距。 */
        private const val CROSSHAIR_BELOW_OFFSET = 32f
    }
}
