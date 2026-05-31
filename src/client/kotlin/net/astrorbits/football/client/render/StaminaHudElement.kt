package net.astrorbits.football.client.render

import net.astrorbits.football.client.StaminaClient
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

/**
 * 体力条 HUD：显示在快捷栏上方，标记 800/400/100 的刻度线。
 */
class StaminaHudElement : HudElement {
    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        val client = Minecraft.getInstance()
        val stamina = StaminaClient.stamina
        // 满体力时不显示
        if (stamina >= StaminaClient.MAX_STAMINA) return

        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight

        val barW = 120
        val barH = 8
        val x = width / 2 - barW / 2
        val y = height - 50  // 快捷栏上方

        val ratio = stamina.toFloat() / StaminaClient.MAX_STAMINA
        val fillW = (barW * ratio).roundToInt().coerceIn(0, barW)

        // 颜色：根据体力区间
        val fillColor = when {
            stamina < 100 -> 0xFFE53935.toInt()  // 红
            stamina < 400 -> 0xFFFF9800.toInt()  // 橙
            stamina < 800 -> 0xFFFFD54F.toInt()  // 黄
            else          -> 0xFF4CAF50.toInt()  // 绿
        }

        // 背景 + 边框
        extra.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, 0xFF555555.toInt())
        extra.fill(x, y, x + barW, y + barH, 0xAA000000.toInt())

        // 体力填充
        if (fillW > 0) {
            extra.fill(x, y, x + fillW, y + barH, fillColor)
        }

        // 刻度线：800, 400, 100
        drawTickMark(extra, x + barW * 800 / StaminaClient.MAX_STAMINA, y, barH)
        drawTickMark(extra, x + barW * 400 / StaminaClient.MAX_STAMINA, y, barH)
        drawTickMark(extra, x + barW * 100 / StaminaClient.MAX_STAMINA, y, barH)
    }

    private fun drawTickMark(extra: GuiGraphicsExtractor, px: Int, y: Int, h: Int) {
        extra.fill(px, y - 2, px + 1, y + h + 2, 0xFFFFFFFF.toInt())
    }
}
