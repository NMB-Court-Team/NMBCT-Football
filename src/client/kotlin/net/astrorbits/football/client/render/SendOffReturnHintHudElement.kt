package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.SendOffLocalClient
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

/** 底部：被罚下球员回归倒计时。 */
class SendOffReturnHintHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!SendOffLocalClient.isActive) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val timeText = SendOffLocalClient.formatRemaining()
        val line = Component.translatable("hud.nmbct-football.send_off.return_hint", timeText).string
        val font = client.font
        val cx = client.window.guiScaledWidth / 2
        val scale = FootballHudTextScale.BOTTOM_HINT
        val y = client.window.guiScaledHeight - FootballHudHintResolver.Layout.BOTTOM_ABOVE_HOTBAR_OFFSET
        val component = Component.literal(line).withStyle(Style.EMPTY.withBold(true))
        val seq = component.visualOrderText
        val w = font.width(seq)
        val color = 0xFFFF5555.toInt()

        val pose = extra.pose()
        pose.pushMatrix()
        pose.translate(cx.toFloat(), y.toFloat())
        pose.scale(scale, scale)
        extra.text(font, seq, -w / 2, 0, color, true)
        pose.popMatrix()
    }
}
