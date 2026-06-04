package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.MatchPauseClient
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class MatchPauseHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!MatchPauseClient.isActive) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val key = if (MatchPauseClient.paused) {
            "hud.nmbct-football.match.paused"
        } else {
            "hud.nmbct-football.match.resumed"
        }

        MatchEventBanner.renderPause(
            extra = extra,
            font = client.font,
            screenW = client.window.guiScaledWidth,
            screenH = client.window.guiScaledHeight,
            elapsedMs = MatchPauseClient.elapsedMs,
            durationMs = MatchPauseClient.DURATION_MS,
            headlineText = Component.translatable(key).string,
        )
    }
}
