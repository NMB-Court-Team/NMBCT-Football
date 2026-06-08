package net.astrorbits.football.client.render

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

class GoalkeeperHoldLockHudElement : HudElement {
    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        val client = Minecraft.getInstance()
        if (client.screen != null || client.isPaused) return
        val player = client.player ?: return
        val level = client.level ?: return

        val content = FootballHudHintResolver.resolve(
            FootballHudHintResolver.Position.BOTTOM_HOLD_LOCK,
            player,
            level,
            delta,
        ) as? FootballHudHintResolver.Content.ProgressBar ?: return

        val bar = content.bar
        val font = client.font
        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        val label = Component.translatable(bar.labelKey).string

        val barW = 120
        val barH = 8
        val x = width / 2 - barW / 2
        val y = height - FootballHudHintResolver.Layout.BOTTOM_HOLD_LOCK_OFFSET
        val fillW = (barW * bar.ratio).roundToInt().coerceIn(0, barW)

        val bg = 0xAA000000.toInt()
        val border = 0xFF555555.toInt()
        val fillColor = 0xFF42A5F5.toInt()

        val labelW = font.width(label)
        extra.text(font, label, width / 2 - labelW / 2, y - font.lineHeight - 2, bar.labelColor, true)

        extra.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, border)
        extra.fill(x, y, x + barW, y + barH, bg)
        if (fillW > 0) {
            extra.fill(x, y, x + fillW, y + barH, fillColor)
        }

        bar.timeText?.let { timeText ->
            val timeW = font.width(timeText)
            extra.text(font, timeText, width / 2 - timeW / 2, y + barH + 2, bar.timeColor, true)
        }
    }
}
