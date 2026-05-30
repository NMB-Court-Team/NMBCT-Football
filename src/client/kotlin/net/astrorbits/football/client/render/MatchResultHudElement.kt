package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.MatchResultClient
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.match.TeamSide
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

class MatchResultHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!MatchResultClient.isActive) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val font = client.font
        val w = client.window.guiScaledWidth
        val h = client.window.guiScaledHeight
        val scale = 3.0f
        val cx = (w / 2f / scale).toInt()
        val baseY = (h / 2f / scale).toInt() - 30

        val remaining = 10000L - MatchResultClient.elapsedMs
        val alpha = ((remaining.coerceIn(0L, 1000L) / 1000f) * 255).toInt()
        val fade = { color: Int -> (alpha shl 24) or (color and 0xFFFFFF) }

        val pose = extra.pose()
        pose.pushMatrix()
        pose.scale(scale, scale)

        val myTeam = MatchStartClient.playerTeam
        val myScore = if (myTeam == TeamSide.A) MatchResultClient.teamAScore else MatchResultClient.teamBScore
        val otherScore = if (myTeam != TeamSide.A) MatchResultClient.teamAScore else MatchResultClient.teamBScore
        val myName = if (myTeam == TeamSide.A) MatchResultClient.teamAName else MatchResultClient.teamBName
        val otherName = if (myTeam != TeamSide.A) MatchResultClient.teamAName else MatchResultClient.teamBName
        val myColor = if (myTeam == TeamSide.A) 0xFFFF5555.toInt() else 0xFF55FFFF.toInt()
        val otherColor = if (myTeam != TeamSide.A) 0xFFFF5555.toInt() else 0xFF55FFFF.toInt()

        var y = baseY

        // result text
        val resultKey: String
        val resultColor: Int
        if (MatchResultClient.isDraw) {
            resultKey = "hud.nmbct-football.result.draw"
            resultColor = 0xFF55FF55.toInt()
        } else if (myScore > otherScore) {
            resultKey = "hud.nmbct-football.result.win"
            resultColor = 0xFFFFAA00.toInt()
        } else {
            resultKey = "hud.nmbct-football.result.loss"
            resultColor = 0xFFFF55AA.toInt()
        }
        val resultText = Component.translatable(resultKey).string
        drawBold(extra, font, resultText, cx - font.width(resultText) / 2, y, fade(resultColor))
        y += 22

        // score line
        val dash = " - "
        val gap = font.width("  ")
        val scoreStr = "$myScore"
        val otherStr = "$otherScore"
        val totalW = font.width(myName) + gap + font.width(scoreStr) + font.width(dash) + font.width(otherStr) + gap + font.width(otherName)
        var sx = cx - totalW / 2
        drawBold(extra, font, myName, sx, y, fade(myColor)); sx += font.width(myName) + gap
        drawBold(extra, font, scoreStr, sx, y, fade(0xFFFFFFFF.toInt())); sx += font.width(scoreStr)
        drawBold(extra, font, dash, sx, y, fade(0xFF888888.toInt())); sx += font.width(dash)
        drawBold(extra, font, otherStr, sx, y, fade(0xFFFFFFFF.toInt())); sx += font.width(otherStr) + gap
        drawBold(extra, font, otherName, sx, y, fade(otherColor))

        pose.popMatrix()
    }

    private fun drawBold(extra: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, text: String, x: Int, y: Int, color: Int) {
        val bold = Component.literal(text).withStyle(Style.EMPTY.withBold(true))
        extra.text(font, bold.visualOrderText, x, y, color, true)
    }
}
