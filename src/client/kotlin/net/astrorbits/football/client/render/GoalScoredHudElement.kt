package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.GoalScoredClient
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.match.TeamSide
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class GoalScoredHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!GoalScoredClient.isActive) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val scoring = GoalScoredClient.scoringTeam
        val topTeam = if (GoalScoredClient.ownGoal) GoalScoredClient.scorerTeam else scoring

        val scoringName = if (scoring == TeamSide.A) MatchStartClient.teamAName else MatchStartClient.teamBName
        val otherName = if (scoring != TeamSide.A) MatchStartClient.teamAName else MatchStartClient.teamBName
        val scoringScore = if (scoring == TeamSide.A) GoalScoredClient.teamAScore else GoalScoredClient.teamBScore
        val otherScore = if (scoring != TeamSide.A) GoalScoredClient.teamAScore else GoalScoredClient.teamBScore

        val headlineText = Component.translatable(
            if (GoalScoredClient.ownGoal) "hud.nmbct-football.goal.headline_own" else "hud.nmbct-football.goal.headline",
        ).string

        MatchEventBanner.render(
            extra = extra,
            font = client.font,
            screenW = client.window.guiScaledWidth,
            screenH = client.window.guiScaledHeight,
            elapsedMs = GoalScoredClient.elapsedMs,
            durationMs = 4000L,
            accentColor = MatchEventBanner.ACCENT_GOLD,
            headline = MatchEventBanner.Line(headlineText, MatchEventBanner.ACCENT_GOLD, bold = true, scale = 1.75f),
            lines = listOf(
                MatchEventBanner.Line(
                    if (topTeam == TeamSide.A) MatchStartClient.teamAName else MatchStartClient.teamBName,
                    MatchEventBanner.teamColor(topTeam),
                    bold = true,
                ),
                MatchEventBanner.Line(GoalScoredClient.scorerName, 0xFFFFFFFF.toInt(), bold = true),
            ),
            scoreRow = MatchEventBanner.ScoreRow(
                nameA = scoringName,
                scoreA = scoringScore,
                nameB = otherName,
                scoreB = otherScore,
                colorA = MatchEventBanner.teamColor(scoring),
                colorB = MatchEventBanner.teamColor(if (scoring == TeamSide.A) TeamSide.B else TeamSide.A),
            ),
        )
    }
}
