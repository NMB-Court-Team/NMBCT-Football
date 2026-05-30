package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.match.TeamSide
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

class HalfKickoffHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!MatchStartClient.isHalfKickoffHudActive) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val font = client.font
        val w = client.window.guiScaledWidth
        val h = client.window.guiScaledHeight
        val scale = 3.0f
        val cx = (w / 2f / scale).toInt()
        val baseY = (h / 2f / scale).toInt() - 24

        val elapsed = System.currentTimeMillis() - MatchStartClient.halfKickoffStartMs
        val remaining = 4000L - elapsed
        val alpha = ((remaining.coerceIn(0L, 1000L) / 1000f) * 255).toInt()
        val fade = { color: Int -> (alpha shl 24) or (color and 0xFFFFFF) }

        val pose = extra.pose()
        pose.pushMatrix()
        pose.scale(scale, scale)

        // phase name
        val phaseText = Component.translatable(MatchStartClient.halfKickoffPhaseKey).string
        drawBold(extra, font, phaseText, cx - font.width(phaseText) / 2, baseY, fade(0xFFFFFFFF.toInt()))

        // team name + " 发球"
        val kt = MatchStartClient.kickoffTeam
        val teamName = if (kt == TeamSide.A) MatchStartClient.teamAName else MatchStartClient.teamBName
        val teamColor = if (kt == TeamSide.A) 0xFFFF5555.toInt() else 0xFF55FFFF.toInt()
        val kickoffLabel = Component.translatable("hud.nmbct-football.half_kickoff.kickoff").string
        val line2 = "$teamName  $kickoffLabel"
        drawBold(extra, font, line2, cx - font.width(line2) / 2, baseY + 20, fade(teamColor))

        pose.popMatrix()
    }

    private fun drawBold(extra: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, text: String, x: Int, y: Int, color: Int) {
        val bold = Component.literal(text).withStyle(Style.EMPTY.withBold(true))
        extra.text(font, bold.visualOrderText, x, y, color, true)
    }
}
