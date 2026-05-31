package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.match.TeamSide
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

class MatchStartHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!MatchStartClient.isHudActive) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val elapsed = MatchStartClient.elapsedMs
        val remaining = 6000L - elapsed
        val phase1 = elapsed < 3000L
        val font = client.font
        val w = client.window.guiScaledWidth
        val h = client.window.guiScaledHeight
        val scale = 3.0f
        val cx = (w / 2f / scale).toInt()
        val baseY = (h / 2f / scale).toInt() - 36

        // fade out in last 1 second
        val alpha = ((remaining.coerceIn(0L, 1000L) / 1000f) * 255).toInt()
        val fade = { color: Int -> (alpha shl 24) or (color and 0xFFFFFF) }

        val pose = extra.pose()
        pose.pushMatrix()
        pose.scale(scale, scale)

        val team = MatchStartClient.playerTeam
        val teamColor = if (team == TeamSide.A) 0xFFFF5555.toInt() else 0xFF55FFFF.toInt()
        val teamText = if (team == TeamSide.A) MatchStartClient.teamAName else MatchStartClient.teamBName

        // team name — bold
        drawBold(extra, font, teamText, cx - font.width(teamText) / 2, baseY, fade(teamColor))

        // role — bold
        val roleKey = if (MatchStartClient.isGk) "hud.nmbct-football.match_start.role_gk" else "hud.nmbct-football.match_start.role_player"
        val roleText = Component.translatable(roleKey).string
        drawBold(extra, font, roleText, cx - font.width(roleText) / 2, baseY + 18, fade(0xFFFFFFFF.toInt()))

        if (phase1) {
            val dots = ".".repeat(((elapsed / 200) % 7).toInt())
            val chText = Component.translatable("hud.nmbct-football.match_start.choosing", dots).string
            drawBold(extra, font, chText, cx - font.width(chText) / 2, baseY + 38, fade(0xFFFFFFAA.toInt()))
        } else {
            val isMine = MatchStartClient.playerTeam == MatchStartClient.kickoffTeam
            val key = if (isMine) "hud.nmbct-football.match_start.our_kickoff" else "hud.nmbct-football.match_start.their_kickoff"
            val color = if (isMine) 0xFF55FF55.toInt() else 0xFFAA55FF.toInt()
            val koText = Component.translatable(key).string
            drawBold(extra, font, koText, cx - font.width(koText) / 2, baseY + 38, fade(color))
        }

        pose.popMatrix()
    }

    /** Draw text in bold using Minecraft's built-in bold font rendering. */
    private fun drawBold(extra: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, text: String, x: Int, y: Int, color: Int) {
        val bold = Component.literal(text).withStyle(Style.EMPTY.withBold(true))
        extra.text(font, bold.visualOrderText, x, y, color, true)
    }
}
