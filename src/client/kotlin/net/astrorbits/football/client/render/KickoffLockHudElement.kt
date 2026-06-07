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
        val client = Minecraft.getInstance()
        if (client.isPaused) return
        val player = client.player ?: return
        if (player.isSpectator) return

        val font = client.font
        val cx = client.window.guiScaledWidth / 2
        val y = client.window.guiScaledHeight - BOTTOM_OFFSET

        if (MatchStartClient.isChoosing) {
            val text = Component.translatable("hud.nmbct-football.kickoff_lock.waiting").string
            drawCenter(extra, font, text, cx, y, COLOR_COUNTDOWN)
            return
        }

        if (!SetPieceRoleHintResolver.isAwaitingSetPieceTouch()) return

        val roleHint = SetPieceRoleHintResolver.resolve(player)
        val inCountdown = MatchStartClient.countdownSeconds > 0 && MatchStartClient.startTimeMs > 0L

        if (inCountdown) {
            val line1 = Component.translatable(
                "hud.nmbct-football.kickoff_lock.countdown",
                MatchStartClient.countdownSeconds.toString(),
            ).string
            drawCenter(extra, font, line1, cx, y, COLOR_COUNTDOWN)
            roleHint?.let { hint ->
                val text = Component.translatable(hint.translationKey).string
                drawCenter(extra, font, text, cx, y + SECOND_LINE_OFFSET, hint.color)
            }
            return
        }

        roleHint?.let { hint ->
            val text = Component.translatable(hint.translationKey).string
            drawCenter(extra, font, text, cx, y, hint.color)
        }
    }

    private fun drawCenter(
        extra: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        text: String,
        cx: Int,
        y: Int,
        color: Int,
    ) {
        val bold = Component.literal(text).withStyle(Style.EMPTY.withBold(true))
        extra.text(font, bold.visualOrderText, cx - font.width(text) / 2, y, color, true)
    }

    companion object {
        private const val BOTTOM_OFFSET = 128
        private const val SECOND_LINE_OFFSET = 14
        private const val COLOR_COUNTDOWN = 0xFFFF5555.toInt()
    }
}
