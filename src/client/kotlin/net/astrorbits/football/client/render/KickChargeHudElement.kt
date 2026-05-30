package net.astrorbits.football.client.render

import net.astrorbits.football.client.key.FootballInputHandler
import net.astrorbits.football.util.KickChargeUtil
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

class KickChargeHudElement : HudElement {
    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        val display = FootballInputHandler.liveKickChargeDisplay() ?: return

        val client = Minecraft.getInstance()
        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        val ratio = display.ratio.coerceIn(0f, 1f)
        val phase = display.phase

        val barW = 120
        val barH = 8
        val x = width / 2 - barW / 2
        val y = height - 42
        val fillW = (barW * ratio).roundToInt().coerceIn(0, barW)

        val bg = 0xAA000000.toInt()
        val border = 0xFF555555.toInt()
        val fillColor = when (phase) {
            KickChargeUtil.Phase.PERFECT -> 0xFF4CAF50.toInt()
            KickChargeUtil.Phase.DECAYING -> 0xFFE53935.toInt()
            KickChargeUtil.Phase.RISING -> when {
                ratio < 0.35f -> 0xFFFFD54F.toInt()
                ratio < 0.7f -> 0xFFFF9800.toInt()
                else -> 0xFFFF5722.toInt()
            }
            KickChargeUtil.Phase.NONE -> 0xFF888888.toInt()
        }

        extra.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, border)
        extra.fill(x, y, x + barW, y + barH, bg)
        if (fillW > 0) {
            extra.fill(x, y, x + fillW, y + barH, fillColor)
        }

        if (phase == KickChargeUtil.Phase.PERFECT) {
            val markerX = x + barW - 2
            extra.fill(markerX, y - 2, markerX + 2, y + barH + 2, 0xFFFFFFFF.toInt())
        }
    }
}