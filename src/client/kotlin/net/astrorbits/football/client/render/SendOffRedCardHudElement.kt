package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.SendOffRedCardClient
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/** 居中：仅被罚下球员可见的红牌 HUD。 */
class SendOffRedCardHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!SendOffRedCardClient.isActive) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val redCardText = Component.translatable("hud.nmbct-football.send_off.red_card").string
        val sentOffText = Component.translatable("hud.nmbct-football.send_off.sent_off").string

        MatchEventBanner.renderRedCardSendOff(
            extra = extra,
            font = client.font,
            screenW = client.window.guiScaledWidth,
            screenH = client.window.guiScaledHeight,
            elapsedMs = SendOffRedCardClient.elapsedMs,
            durationMs = 6000L,
            redCardText = redCardText,
            sentOffText = sentOffText,
            teamColor = MatchEventBanner.teamColor(SendOffRedCardClient.team),
        )
    }
}
