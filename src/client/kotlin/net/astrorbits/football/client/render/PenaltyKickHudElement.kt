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
        val teamName = when (team) {
            TeamSide.A -> PenaltyKickClient.teamAName.ifBlank { MatchHudTeams.name(TeamSide.A) }
            TeamSide.B -> PenaltyKickClient.teamBName.ifBlank { MatchHudTeams.name(TeamSide.B) }
        }
        val headline = if (PenaltyKickClient.suddenDeath) {
            Component.translatable("hud.nmbct-football.penalty.kick_sudden_death", PenaltyKickClient.kickNumber).string
        } else {
            Component.translatable("hud.nmbct-football.penalty.kick_round", PenaltyKickClient.kickNumber).string
        }

        MatchEventBanner.renderGoal(
            extra = extra,
            font = client.font,
            screenW = client.window.guiScaledWidth,
            screenH = client.window.guiScaledHeight,
            elapsedMs = PenaltyKickClient.elapsedMs,
            durationMs = 3500L,
            ownGoal = false,
            headline = headline,
            teamLine = teamName,
            teamColor = MatchEventBanner.teamColor(team),
            scorerLine = PenaltyKickClient.kickerName,
            scoreRow = MatchEventBanner.ScoreRow(
                nameA = PenaltyKickClient.teamAName.ifBlank { MatchHudTeams.name(TeamSide.A) },
                scoreA = PenaltyKickClient.penaltyScoreA,
                nameB = PenaltyKickClient.teamBName.ifBlank { MatchHudTeams.name(TeamSide.B) },
                scoreB = PenaltyKickClient.penaltyScoreB,
                colorA = MatchEventBanner.teamColor(TeamSide.A),
                colorB = MatchEventBanner.teamColor(TeamSide.B),
            ),
        )
    }
}
