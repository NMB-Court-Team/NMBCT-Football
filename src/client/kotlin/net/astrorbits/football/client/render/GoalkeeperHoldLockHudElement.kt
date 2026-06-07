package net.astrorbits.football.client.render

import net.astrorbits.football.client.GoalkeeperStateClient
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

class GoalkeeperHoldLockHudElement : HudElement {
    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        val client = Minecraft.getInstance()
        if (client.screen != null || client.isPaused) {
            return
        }
        if (!GoalkeeperStateClient.isGoalkeeper && !GoalkeeperStateClient.isHoldingBall) {
            return
        }

        val ratio = GoalkeeperStateClient.liveHoldReleaseLockRatio(delta.getGameTimeDeltaTicks())
        if (ratio <= 0f) {
            return
        }

        val font = client.font
        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        val label = Component.translatable(LABEL_KEY).string

        val barW = 120
        val barH = 8
        val x = width / 2 - barW / 2
        val y = height - 92
        val fillW = (barW * ratio).roundToInt().coerceIn(0, barW)

        val bg = 0xAA000000.toInt()
        val border = 0xFF555555.toInt()
        val fillColor = 0xFF42A5F5.toInt()

        val labelW = font.width(label)
        extra.text(font, label, width / 2 - labelW / 2, y - font.lineHeight - 2, LABEL_COLOR, true)

        extra.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, border)
        extra.fill(x, y, x + barW, y + barH, bg)
        if (fillW > 0) {
            extra.fill(x, y, x + fillW, y + barH, fillColor)
        }

        val secondsLeft = ratio * GoalkeeperStateClient.holdReleaseLockTotalTicks() / 20f
        val timeText = String.format("%.1fs", secondsLeft)
        val timeW = font.width(timeText)
        extra.text(font, timeText, width / 2 - timeW / 2, y + barH + 2, TIME_COLOR, true)
    }

    companion object {
        private const val LABEL_KEY = "hud.nmbct-football.gk_hold_lock"
        private const val LABEL_COLOR = 0xFF90CAF9.toInt()
        private const val TIME_COLOR = 0xFFCCCCCC.toInt()
    }
}