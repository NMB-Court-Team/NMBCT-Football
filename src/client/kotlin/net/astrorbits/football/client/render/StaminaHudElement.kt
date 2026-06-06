package net.astrorbits.football.client.render

import net.astrorbits.football.client.StaminaClient
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

/**
 * 体力条 HUD：显示在快捷栏上方，按配置档位绘制刻度线。
 */
class StaminaHudElement : HudElement {
    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        val client = Minecraft.getInstance()
        if (client.player?.isCreative == true) return

        val stamina = StaminaClient.stamina
        val maxStamina = StaminaClient.maxStamina
        if (stamina >= maxStamina - 1e-3f && StaminaClient.boostBlend <= 0f) return

        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight

        val barW = 120
        val barH = 8
        val x = width / 2 - barW / 2
        val y = height - 68

        val ratio = (stamina / maxStamina).coerceIn(0f, 1f)
        val fillW = (barW * ratio).roundToInt().coerceIn(0, barW)

        val fillColor = StaminaClient.displayStaminaBarColor(ratio)

        extra.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, 0xFF555555.toInt())
        extra.fill(x, y, x + barW, y + barH, 0xAA000000.toInt())

        if (fillW > 0) {
            extra.fill(x, y, x + fillW, y + barH, fillColor)
        }

        for (fraction in StaminaClient.hudTierFractions()) {
            val tickX = x + (barW * fraction).roundToInt()
            drawTickMark(extra, tickX, y, barH)
        }
    }

    private fun drawTickMark(extra: GuiGraphicsExtractor, px: Int, y: Int, h: Int) {
        extra.fill(px, y - 2, px + 1, y + h + 2, 0xFFFFFFFF.toInt())
    }
}
