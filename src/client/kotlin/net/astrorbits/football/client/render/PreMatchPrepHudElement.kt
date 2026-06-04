package net.astrorbits.football.client.render

import net.astrorbits.football.client.util.FootballHudVisibility
import net.astrorbits.football.match.MatchState
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/** 赛前准备阶段：屏幕上方持续显示亮绿色 Banner；计时暂停时合并「比赛暂停中」大字。 */
class PreMatchPrepHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!MatchState.isPreMatchPreparationPhase()) return
        val client = Minecraft.getInstance()
        if (client.isPaused || FootballHudVisibility.isDebugOverlayOpen(client)) return

        val timerPaused = MatchState.isMatchTimerPaused()
        val headline = Component.translatable("match.phase.pre_match_prep").string
        val remaining = MatchState.formatElapsed(MatchState.getPhaseRemainingTicks())
        val timerLine = Component.translatable("hud.nmbct-football.prep.remaining", remaining).string
        val pauseOverlay = if (timerPaused) {
            Component.translatable("hud.nmbct-football.match.paused_overlay").string
        } else {
            null
        }

        MatchEventBanner.renderPreMatchPrep(
            extra = extra,
            font = client.font,
            screenW = client.window.guiScaledWidth,
            screenH = client.window.guiScaledHeight,
            headlineText = headline,
            timerText = timerLine,
            timerPaused = timerPaused,
            pauseOverlayText = pauseOverlay,
        )
    }
}
