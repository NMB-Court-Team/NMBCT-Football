package net.astrorbits.football.client.render

import net.astrorbits.football.client.FootballOperabilityClient
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

        val myTeam = client.player?.let { FootballOperabilityClient.resolveLocalPlayerTeam(it) }
            ?: MatchStartClient.playerTeam
        val otherTeam = myTeam.opponent()
        val myScore = if (myTeam == TeamSide.A) MatchResultClient.teamAScore else MatchResultClient.teamBScore
        val otherScore = if (myTeam != TeamSide.A) MatchResultClient.teamAScore else MatchResultClient.teamBScore
        val myName = (if (myTeam == TeamSide.A) MatchResultClient.teamAName else MatchResultClient.teamBName)
            .ifBlank { MatchHudTeams.name(myTeam) }
        val otherName = (if (myTeam != TeamSide.A) MatchResultClient.teamAName else MatchResultClient.teamBName)
            .ifBlank { MatchHudTeams.name(otherTeam) }

        val (resultKey, resultColor, accent) = when {
            MatchResultClient.forfeitWinner != null -> {
                if (MatchResultClient.forfeitWinner == myTeam) {
                    Triple("hud.nmbct-football.result.win_forfeit", MatchEventBanner.ACCENT_WIN, MatchEventBanner.ACCENT_WIN)
                } else {
                    Triple("hud.nmbct-football.result.loss_forfeit", MatchEventBanner.ACCENT_LOSS, MatchEventBanner.ACCENT_LOSS)
                }
            }
            MatchResultClient.isDraw && !MatchResultClient.wonByPenalties -> Triple(
                "hud.nmbct-football.result.draw",
                MatchEventBanner.ACCENT_DRAW,
                MatchEventBanner.ACCENT_DRAW,
            )
            MatchResultClient.wonByPenalties -> {
                val winner = MatchResultClient.penaltyWinner
                val iWon = when (winner) {
                    myTeam -> true
                    null -> {
                        val myPen = if (myTeam == TeamSide.A) MatchResultClient.penaltyScoreA else MatchResultClient.penaltyScoreB
                        val otherPen = if (myTeam == TeamSide.A) MatchResultClient.penaltyScoreB else MatchResultClient.penaltyScoreA
                        myPen > otherPen
                    }
                    else -> false
                }
                if (iWon) {
                    Triple("hud.nmbct-football.result.win_penalties", MatchEventBanner.ACCENT_WIN, MatchEventBanner.ACCENT_WIN)
                } else {
                    Triple("hud.nmbct-football.result.loss_penalties", MatchEventBanner.ACCENT_LOSS, MatchEventBanner.ACCENT_LOSS)
                }
            }
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
            scoreRow = if (MatchResultClient.wonByPenalties) {
                MatchEventBanner.ScoreRow(
                    nameA = myName,
                    scoreA = if (myTeam == TeamSide.A) MatchResultClient.penaltyScoreA else MatchResultClient.penaltyScoreB,
                    nameB = otherName,
                    scoreB = if (myTeam == TeamSide.A) MatchResultClient.penaltyScoreB else MatchResultClient.penaltyScoreA,
                    colorA = MatchEventBanner.teamColor(myTeam),
                    colorB = MatchEventBanner.teamColor(otherTeam),
                )
            } else {
                MatchEventBanner.ScoreRow(
                    nameA = myName,
                    scoreA = myScore,
                    nameB = otherName,
                    scoreB = otherScore,
                    colorA = MatchEventBanner.teamColor(myTeam),
                    colorB = MatchEventBanner.teamColor(otherTeam),
                )
            },
        )
    }
}
