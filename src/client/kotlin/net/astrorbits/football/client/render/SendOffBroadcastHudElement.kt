package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.SendOffBroadcastClient
import net.astrorbits.football.match.MatchConfigHolder
import net.astrorbits.football.match.MatchState
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/** 左侧：全员可见的罚下广播。 */
class SendOffBroadcastHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!SendOffBroadcastClient.isActive) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val name = SendOffBroadcastClient.playerName.ifBlank { "?" }
        val headline = Component.translatable("hud.nmbct-football.send_off.broadcast_line", name).string
        val durationText = MatchState.formatElapsed(
            MatchConfigHolder.current.sendOffDurationSeconds.coerceAtLeast(0) * 20,
        )
        val detail = Component.translatable("hud.nmbct-football.send_off.broadcast_detail", durationText).string

        MatchEventBanner.renderSendOffBroadcast(
            extra = extra,
            font = client.font,
            anchorX = ANCHOR_X,
            anchorY = ANCHOR_Y,
            elapsedMs = SendOffBroadcastClient.elapsedMs,
            durationMs = 6000L,
            headline = headline,
            detailLine = detail,
            teamColor = MatchEventBanner.teamColor(SendOffBroadcastClient.team),
        )
    }

    companion object {
        private const val ANCHOR_X = 8
        private const val ANCHOR_Y = 36
    }
}
