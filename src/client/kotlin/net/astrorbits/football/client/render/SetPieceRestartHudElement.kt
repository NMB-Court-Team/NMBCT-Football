package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.MatchHudTeams
import net.astrorbits.football.client.match.SetPieceRestartClient
import net.astrorbits.football.match.SetPieceRestartKind
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class SetPieceRestartHudElement : HudElement {
    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!SetPieceRestartClient.isActive) return
        if (SetPieceRestartClient.elapsedMs >= 4000L) {
            SetPieceRestartClient.hide()
            return
        }
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val typeKey = when (SetPieceRestartClient.kind) {
            SetPieceRestartKind.KICKOFF -> "hud.nmbct-football.restart.kickoff"
            SetPieceRestartKind.GOAL_KICK -> "hud.nmbct-football.restart.goal_kick"
            SetPieceRestartKind.CORNER_KICK -> "hud.nmbct-football.restart.corner_kick"
            SetPieceRestartKind.THROW_IN -> "hud.nmbct-football.restart.throw_in"
        }
        val restartName = MatchHudTeams.name(SetPieceRestartClient.restartTeam)
        val restartLine = Component.translatable("hud.nmbct-football.free_kick.restart", restartName).string

        MatchEventBanner.renderFreeKick(
            extra = extra,
            font = client.font,
            screenW = client.window.guiScaledWidth,
            screenH = client.window.guiScaledHeight,
            elapsedMs = SetPieceRestartClient.elapsedMs,
            durationMs = 4000L,
            typeText = Component.translatable(typeKey).string,
            foulLine = "",
            foulPlayerColor = 0xFFCCCCCC.toInt(),
            restartLine = restartLine,
            restartColor = MatchEventBanner.teamColor(SetPieceRestartClient.restartTeam),
        )
    }
}
