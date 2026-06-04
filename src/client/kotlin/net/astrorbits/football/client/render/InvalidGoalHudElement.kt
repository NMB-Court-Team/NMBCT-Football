package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.InvalidGoalClient
import net.astrorbits.football.client.match.MatchHudTeams
import net.astrorbits.football.match.TeamSide
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class InvalidGoalHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!InvalidGoalClient.isActive) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        fun teamName(side: TeamSide): String {
            val fromPayload = InvalidGoalClient.name(side)
            return fromPayload.ifBlank { MatchHudTeams.name(side) }
        }

        val headlineText = Component.translatable("hud.nmbct-football.invalid_goal.headline").string
        val scorerLine = Component.translatable(
            "hud.nmbct-football.invalid_goal.scorer",
            InvalidGoalClient.scorerName,
        ).string

        MatchEventBanner.renderInvalidGoal(
            extra = extra,
            font = client.font,
            screenW = client.window.guiScaledWidth,
            screenH = client.window.guiScaledHeight,
            elapsedMs = InvalidGoalClient.elapsedMs,
            durationMs = 4000L,
            headline = headlineText,
            scorerLine = scorerLine,
            scorerColor = MatchEventBanner.teamColor(InvalidGoalClient.scorerTeam),
            scoreRow = MatchEventBanner.ScoreRow(
                nameA = teamName(TeamSide.A),
                scoreA = InvalidGoalClient.teamAScore,
                nameB = teamName(TeamSide.B),
                scoreB = InvalidGoalClient.teamBScore,
                colorA = MatchEventBanner.teamColor(TeamSide.A),
                colorB = MatchEventBanner.teamColor(TeamSide.B),
            ),
        )
    }
}
