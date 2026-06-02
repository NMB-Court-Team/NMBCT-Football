package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.MatchHudTeams
import net.astrorbits.football.client.match.MatchResultClient
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.match.TeamSide
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class MatchResultHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!MatchResultClient.isActive) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val myTeam = MatchStartClient.playerTeam
        val otherTeam = if (myTeam == TeamSide.A) TeamSide.B else TeamSide.A
        val myScore = if (myTeam == TeamSide.A) MatchResultClient.teamAScore else MatchResultClient.teamBScore
        val otherScore = if (myTeam != TeamSide.A) MatchResultClient.teamAScore else MatchResultClient.teamBScore
        val myName = (if (myTeam == TeamSide.A) MatchResultClient.teamAName else MatchResultClient.teamBName)
            .ifBlank { MatchHudTeams.name(myTeam) }
        val otherName = (if (myTeam != TeamSide.A) MatchResultClient.teamAName else MatchResultClient.teamBName)
            .ifBlank { MatchHudTeams.name(otherTeam) }

        val (resultKey, resultColor, accent) = when {
            MatchResultClient.isDraw -> Triple(
                "hud.nmbct-football.result.draw",
                MatchEventBanner.ACCENT_DRAW,
                MatchEventBanner.ACCENT_DRAW,
            )
            myScore > otherScore -> Triple(
                "hud.nmbct-football.result.win",
                MatchEventBanner.ACCENT_WIN,
                MatchEventBanner.ACCENT_WIN,
            )
            else -> Triple(
                "hud.nmbct-football.result.loss",
                MatchEventBanner.ACCENT_LOSS,
                MatchEventBanner.ACCENT_LOSS,
            )
        }

        MatchEventBanner.renderResult(
            extra = extra,
            font = client.font,
            screenW = client.window.guiScaledWidth,
            screenH = client.window.guiScaledHeight,
            elapsedMs = MatchResultClient.elapsedMs,
            durationMs = 10000L,
            resultText = Component.translatable(resultKey).string,
            resultColor = resultColor,
            accentColor = accent,
            scoreRow = MatchEventBanner.ScoreRow(
                nameA = myName,
                scoreA = myScore,
                nameB = otherName,
                scoreB = otherScore,
                colorA = MatchEventBanner.teamColor(myTeam),
                colorB = MatchEventBanner.teamColor(otherTeam),
            ),
        )
    }
}
