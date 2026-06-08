package net.astrorbits.football.client.render

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

class KickoffLockHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        val client = Minecraft.getInstance()
        if (client.isPaused) return
        val player = client.player ?: return
        val level = client.level ?: return

        val content = FootballHudHintResolver.resolve(
            FootballHudHintResolver.Position.BOTTOM_ABOVE_HOTBAR,
            player,
            level,
            delta,
        ) as? FootballHudHintResolver.Content.TextLines ?: return

        val font = client.font
        val cx = client.window.guiScaledWidth / 2
        var y = client.window.guiScaledHeight - FootballHudHintResolver.Layout.BOTTOM_ABOVE_HOTBAR_OFFSET

        for (line in content.lines) {
            drawCenter(extra, font, line.format(), cx, y, line.color, line.bold)
            y += FootballHudHintResolver.Layout.BOTTOM_LINE_GAP
        }
    }

    private fun drawCenter(
        extra: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        text: String,
        cx: Int,
        y: Int,
        color: Int,
        bold: Boolean,
    ) {
        val component = if (bold) {
            Component.literal(text).withStyle(Style.EMPTY.withBold(true))
        } else {
            Component.literal(text)
        }
        extra.text(font, component.visualOrderText, cx - font.width(text) / 2, y, color, true)
    }
}
