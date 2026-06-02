package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.GoalScoredClient
import net.astrorbits.football.client.match.MatchHudTeams
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

        fun teamName(side: TeamSide): String {
            val fromPayload = GoalScoredClient.name(side)
            return fromPayload.ifBlank { MatchHudTeams.name(side) }
        }

        val scoringName = teamName(scoring)
        val otherName = teamName(if (scoring == TeamSide.A) TeamSide.B else TeamSide.A)
        val scoringScore = if (scoring == TeamSide.A) GoalScoredClient.teamAScore else GoalScoredClient.teamBScore
        val otherScore = if (scoring != TeamSide.A) GoalScoredClient.teamAScore else GoalScoredClient.teamBScore

        val headlineText = Component.translatable(
            if (GoalScoredClient.ownGoal) "hud.nmbct-football.goal.headline_own" else "hud.nmbct-football.goal.headline",
        ).string
        val topName = teamName(topTeam)

        MatchEventBanner.renderGoal(
            extra = extra,
            font = client.font,
            screenW = client.window.guiScaledWidth,
            screenH = client.window.guiScaledHeight,
            elapsedMs = GoalScoredClient.elapsedMs,
            durationMs = 4000L,
            ownGoal = GoalScoredClient.ownGoal,
            headline = headlineText,
            teamLine = topName,
            teamColor = MatchEventBanner.teamColor(topTeam),
            scorerLine = GoalScoredClient.scorerName,
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
