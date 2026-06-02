package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.match.TeamSide
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class MatchStartHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!MatchStartClient.isHudActive) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val elapsed = MatchStartClient.elapsedMs
        val phase1 = elapsed < 3000L
        val team = MatchStartClient.playerTeam
        val teamText = if (team == TeamSide.A) MatchStartClient.teamAName else MatchStartClient.teamBName
        val teamColor = MatchEventBanner.teamColor(team)

        val roleKey = if (MatchStartClient.isGk) {
            "hud.nmbct-football.match_start.role_gk"
        } else {
            "hud.nmbct-football.match_start.role_player"
        }
        val roleText = Component.translatable(roleKey).string

        val statusLine = if (phase1) {
            val dots = ".".repeat(((elapsed / 200) % 7).toInt())
            MatchEventBanner.Line(
                Component.translatable("hud.nmbct-football.match_start.choosing", dots).string,
                0xFFFFFFAA.toInt(),
            )
        } else {
            val isMine = MatchStartClient.playerTeam == MatchStartClient.kickoffTeam
            val key = if (isMine) {
                "hud.nmbct-football.match_start.our_kickoff"
            } else {
                "hud.nmbct-football.match_start.their_kickoff"
            }
            val color = if (isMine) 0xFF55FF55.toInt() else 0xFFAA55FF.toInt()
            MatchEventBanner.Line(Component.translatable(key).string, color, bold = true)
        }

        MatchEventBanner.render(
            extra = extra,
            font = client.font,
            screenW = client.window.guiScaledWidth,
            screenH = client.window.guiScaledHeight,
            elapsedMs = elapsed,
            durationMs = 6000L,
            accentColor = teamColor,
            headline = MatchEventBanner.Line(teamText, teamColor, bold = true, scale = 1.75f),
            lines = listOf(
                MatchEventBanner.Line(roleText, 0xFFCCCCCC.toInt()),
                statusLine,
            ),
        )
    }
}
