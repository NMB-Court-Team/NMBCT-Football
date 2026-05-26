package net.astrorbits.football.client

import net.astrorbits.football.match.MatchState
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

class FootballHudElement : HudElement {

	override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
		val font = Minecraft.getInstance().font
		val timerText = MatchState.formatTime()
		val teamA = MatchState.teamAName
		val teamB = MatchState.teamBName
		val scoreA = MatchState.teamAScore.toString()
		val scoreB = MatchState.teamBScore.toString()

		val timerW = font.width(timerText)
		val teamAW = font.width(teamA)
		val teamBW = font.width(teamB)
		val scoreAW = font.width(scoreA)
		val scoreBW = font.width(scoreB)
		val sepW = font.width(SEP)

		val rowW = timerW + GAP_L + teamAW + GAP_S + scoreAW + GAP_S + sepW + GAP_S + scoreBW + GAP_L + teamBW
		val panelW = rowW + PAD * 2 + BAR_W
		val panelH = font.lineHeight + PAD * 2

		// Background
		extra.fill(BAR_X, BAR_Y, BAR_X + panelW, BAR_Y + panelH, BG)
		// Accent bar
		extra.fill(BAR_X, BAR_Y, BAR_X + BAR_W, BAR_Y + panelH, ACCENT)

		val y = BAR_Y + PAD
		var x = BAR_X + BAR_W + PAD

		// Timer
		extra.text(font, timerText, x, y, TIMER, false)
		x += timerW + GAP_L

		// Team A
		extra.text(font, teamA, x, y, TEAM_A, false)
		x += teamAW + GAP_S

		// Score A
		extra.text(font, scoreA, x, y, WHITE, false)
		x += scoreAW + GAP_S

		// Separator
		extra.text(font, SEP, x, y, GRAY, false)
		x += sepW + GAP_S

		// Score B
		extra.text(font, scoreB, x, y, WHITE, false)
		x += scoreBW + GAP_L

		// Team B
		extra.text(font, teamB, x, y, TEAM_B, false)
	}

	companion object {
		private const val BAR_X = 10
		private const val BAR_Y = 10
		private const val BAR_W = 2
		private const val PAD = 10

		private const val GAP_L = 14
		private const val GAP_S = 8

		private const val SEP = "-"

		private val BG = 0xCC0A0A14.toInt()
		private val ACCENT = 0xFFFFD700.toInt()
		private val TIMER = 0xFFCCCCCC.toInt()
		private val TEAM_A = 0xFFFF6B6B.toInt()
		private val TEAM_B = 0xFF4ECDC4.toInt()
		private val WHITE = 0xFFFFFFFF.toInt()
		private val GRAY = 0xFF666666.toInt()
	}
}
