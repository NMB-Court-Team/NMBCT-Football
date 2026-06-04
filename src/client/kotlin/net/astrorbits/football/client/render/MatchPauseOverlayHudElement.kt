package net.astrorbits.football.client.render

import net.astrorbits.football.match.MatchState
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

/** 比赛暂停期间屏幕顶端持续显示的大字提示（与 4s Banner 并存）。 */
class MatchPauseOverlayHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!MatchState.isDuringMatch() || MatchState.isRunning) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val font = client.font
        val screenW = client.window.guiScaledWidth
        val text = Component.translatable(LABEL_KEY)
            .withStyle(Style.EMPTY.withBold(true))
            .visualOrderText
        val w = font.width(text)
        val cx = screenW / 2
        val y = TOP_MARGIN

        val pose = extra.pose()
        pose.pushMatrix()
        pose.translate(cx - w * TEXT_SCALE / 2f, y.toFloat())
        pose.scale(TEXT_SCALE, TEXT_SCALE)
        extra.text(font, text, 0, 0, MatchEventBanner.ACCENT_PAUSE, true)
        pose.popMatrix()
    }

    companion object {
        private const val LABEL_KEY = "hud.nmbct-football.match.paused_overlay"
        private const val TEXT_SCALE = 2f
        private const val TOP_MARGIN = 10
    }
}
