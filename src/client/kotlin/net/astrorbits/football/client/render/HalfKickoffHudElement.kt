package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.MatchHudTeams
import net.astrorbits.football.client.match.MatchStartClient
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class HalfKickoffHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!MatchStartClient.isHalfKickoffHudActive) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val kt = MatchStartClient.kickoffTeam
        val teamName = MatchHudTeams.name(kt)
        val teamColor = MatchEventBanner.teamColor(kt)
        val phaseText = Component.translatable(MatchStartClient.halfKickoffPhaseKey).string
        val kickoffLabel = Component.translatable("hud.nmbct-football.half_kickoff.kickoff").string
        val elapsed = System.currentTimeMillis() - MatchStartClient.halfKickoffStartMs
        val combined = "$phaseText · $teamName $kickoffLabel"

        MatchEventBanner.renderHalf(
            extra = extra,
            font = client.font,
            screenW = client.window.guiScaledWidth,
            screenH = client.window.guiScaledHeight,
            elapsedMs = elapsed,
            durationMs = 4000L,
            combinedText = combined,
            phaseColor = 0xFFFFFFFF.toInt(),
            kickoffColor = teamColor,
        )
    }
}
