package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.MatchHudTeams
import net.astrorbits.football.client.match.PenaltyKickClient
import net.astrorbits.football.match.TeamSide
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class PenaltyKickHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!PenaltyKickClient.isActive) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val team = PenaltyKickClient.kickerTeam
        val teamAName = PenaltyKickClient.teamAName.ifBlank { MatchHudTeams.name(TeamSide.A) }
        val teamBName = PenaltyKickClient.teamBName.ifBlank { MatchHudTeams.name(TeamSide.B) }
        val kickerDisplay = PenaltyKickClient.kickerName.ifBlank { "?" }
        val headline = if (PenaltyKickClient.suddenDeath) {
            Component.translatable("hud.nmbct-football.penalty.kick_sudden_death", PenaltyKickClient.kickNumber).string
        } else {
            Component.translatable("hud.nmbct-football.penalty.kick_round", PenaltyKickClient.kickNumber).string
        }

        val kickingName = if (team == TeamSide.A) teamAName else teamBName
        val waitingName = if (team == TeamSide.A) teamBName else teamAName
        val kickingScore = if (team == TeamSide.A) PenaltyKickClient.penaltyScoreA else PenaltyKickClient.penaltyScoreB
        val waitingScore = if (team == TeamSide.A) PenaltyKickClient.penaltyScoreB else PenaltyKickClient.penaltyScoreA

        MatchEventBanner.renderGoal(
            extra = extra,
            font = client.font,
            screenW = client.window.guiScaledWidth,
            screenH = client.window.guiScaledHeight,
            elapsedMs = PenaltyKickClient.elapsedMs,
            durationMs = 3500L,
            ownGoal = false,
            headline = headline,
            teamLine = kickerDisplay,
            teamColor = MatchEventBanner.teamColor(team),
            scorerLine = "",
            scoreRow = MatchEventBanner.ScoreRow(
                nameA = kickingName,
                scoreA = kickingScore,
                nameB = waitingName,
                scoreB = waitingScore,
                colorA = MatchEventBanner.teamColor(team),
                colorB = MatchEventBanner.teamColor(team.opponent()),
            ),
        )
    }
}
