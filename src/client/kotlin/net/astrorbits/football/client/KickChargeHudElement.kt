package net.astrorbits.football.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

class KickChargeHudElement : HudElement {
    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!FootballInputHandler.isChargingShoot) {
            return
        }

        val client = Minecraft.getInstance()
        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        val ratio = FootballInputHandler.shootChargeRatio.coerceIn(0f, 1f)

        val barW = 120
        val barH = 8
        val x = width / 2 - barW / 2
        val y = height - 42
        val fillW = (barW * ratio).toInt().coerceAtLeast(1)

        val bg = 0xAA000000.toInt()
        val border = 0xFF555555.toInt()
        val fillColor = when {
            ratio < 0.35f -> 0xFFFFD54F.toInt()
            ratio < 0.7f -> 0xFFFF9800.toInt()
            else -> 0xFFFF5722.toInt()
        }

        extra.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, border)
        extra.fill(x, y, x + barW, y + barH, bg)
        extra.fill(x, y, x + fillW, y + barH, fillColor)
    }
}
