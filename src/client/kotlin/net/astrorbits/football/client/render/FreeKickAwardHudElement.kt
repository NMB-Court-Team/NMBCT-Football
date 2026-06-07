package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.FreeKickAwardClient
import net.astrorbits.football.client.match.MatchHudTeams
import net.astrorbits.football.match.FreeKickFoulReason
import net.astrorbits.football.match.FreeKickType
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class FreeKickAwardHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!FreeKickAwardClient.isActive) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val typeKey = when (FreeKickAwardClient.freeKickType) {
            FreeKickType.DIRECT -> "hud.nmbct-football.free_kick.type.direct"
            FreeKickType.INDIRECT -> "hud.nmbct-football.free_kick.type.indirect"
            FreeKickType.PENALTY -> "hud.nmbct-football.free_kick.type.penalty"
        }
        val reasonKey = when (FreeKickAwardClient.foulReason) {
            FreeKickFoulReason.OFFSIDE -> "hud.nmbct-football.free_kick.reason.offside"
            FreeKickFoulReason.GOALKEEPER_LEFT_PENALTY_AREA ->
                "hud.nmbct-football.free_kick.reason.goalkeeper_left_penalty_area"
            FreeKickFoulReason.SLIDE_TACKLE_IN_PENALTY_AREA ->
                "hud.nmbct-football.free_kick.reason.slide_tackle_in_penalty_area"
            FreeKickFoulReason.SECOND_TOUCH ->
                "hud.nmbct-football.free_kick.reason.second_touch"
        }
        val reasonText = Component.translatable(reasonKey).string
        val playerName = FreeKickAwardClient.foulingPlayerName.ifBlank { "?" }
        val foulLine = Component.translatable(
            "hud.nmbct-football.free_kick.foul_line",
            reasonText,
            playerName,
        ).string
        val restartName = MatchHudTeams.name(FreeKickAwardClient.restartTeam)
        val restartLine = Component.translatable("hud.nmbct-football.free_kick.restart", restartName).string

        MatchEventBanner.renderFreeKick(
            extra = extra,
            font = client.font,
            screenW = client.window.guiScaledWidth,
            screenH = client.window.guiScaledHeight,
            elapsedMs = FreeKickAwardClient.elapsedMs,
            durationMs = 4000L,
            typeText = Component.translatable(typeKey).string,
            foulLine = foulLine,
            foulPlayerColor = MatchEventBanner.teamColor(FreeKickAwardClient.foulingTeam),
            restartLine = restartLine,
            restartColor = MatchEventBanner.teamColor(FreeKickAwardClient.restartTeam),
        )
    }
}
