package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.MatchStartClient
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

class KickoffLockHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!MatchStartClient.isLocked) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val font = client.font
        val w = client.window.guiScaledWidth
        val y = client.window.guiScaledHeight - 86

        if (MatchStartClient.isChoosing) {
            val text = Component.translatable("hud.nmbct-football.kickoff_lock.waiting").string
            drawCenter(extra, font, text, w / 2, y, 0xFFFF5555.toInt())
        } else if (MatchStartClient.countdownSeconds > 0) {
            val line1 = Component.translatable("hud.nmbct-football.kickoff_lock.countdown", MatchStartClient.countdownSeconds.toString()).string
            drawCenter(extra, font, line1, w / 2, y, 0xFFFF5555.toInt())
            if (!MatchStartClient.isKickoffTeam) {
                val line2 = Component.translatable("hud.nmbct-football.kickoff_lock.wait_opponent").string
                drawCenter(extra, font, line2, w / 2, y + 14, 0xFFFFAA00.toInt())
            }
        } else if (!MatchStartClient.isKickoffTeam) {
            val text = Component.translatable("hud.nmbct-football.kickoff_lock.wait_touch").string
            drawCenter(extra, font, text, w / 2, y, 0xFFFFAA00.toInt())
        }
    }

    private fun drawCenter(extra: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, text: String, cx: Int, y: Int, color: Int) {
        val bold = Component.literal(text).withStyle(Style.EMPTY.withBold(true))
        extra.text(font, bold.visualOrderText, cx - font.width(text) / 2, y, color, true)
    }
}
