package net.astrorbits.football.client

import net.astrorbits.football.match.MatchState
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

class FootballHudElement : HudElement {

	override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
		val font = Minecraft.getInstance().font
		val timer = MatchState.formatTime()
		val teamA = MatchState.teamAName.visualOrderText
		val teamB = MatchState.teamBName.visualOrderText
		val scoreA = Component.literal(MatchState.teamAScore.toString()).visualOrderText
		val scoreB = Component.literal(MatchState.teamBScore.toString()).visualOrderText

		val panelW = PAD + T_W + GAP + NAME_W + GAP + S_W + GAP + D_W + GAP + S_W + GAP + NAME_W + PAD
		val cy = Y + H / 2 - font.lineHeight / 2

		extra.fill(X, Y, X + panelW, Y + H, BAR)

		var cx = X + PAD

		extra.text(font, timer, cx, cy, TIMER, true)
		cx += T_W + GAP

		cx += drawName(extra, font, teamA, cx, cy, RED, true)
		cx += GAP
		cx += drawScore(extra, font, scoreA, cx, cy)
		cx += GAP
		extra.text(font, SEP, cx + D_W / 2 - font.width(SEP) / 2, cy, DIM, true)
		cx += D_W + GAP
		cx += drawScore(extra, font, scoreB, cx, cy)
		cx += GAP
		drawName(extra, font, teamB, cx, cy, CYAN, false)
	}

	private fun drawName(extra: GuiGraphicsExtractor, font: Font, text: FormattedCharSequence, x: Int, y: Int, color: Int, rightAlign: Boolean): Int {
		val w = font.width(text)
		val tx = if (rightAlign) x + NAME_W - w else x
		extra.text(font, text, tx, y, color, true)
		return NAME_W
	}

	private fun drawScore(extra: GuiGraphicsExtractor, font: Font, text: FormattedCharSequence, x: Int, y: Int): Int {
		extra.fill(x, Y + 3, x + S_W, Y + H - 3, SCORE_BOX)
		extra.text(font, text, x + S_W / 2 - font.width(text) / 2, y, WHITE, true)
		return S_W
	}

	companion object {
		private const val SEP = "-"

		private const val X = 8; private const val Y = 8; private const val H = 22
		private const val PAD = 10
		private const val T_W = 42; private const val NAME_W = 48
		private const val S_W = 26; private const val D_W = 14
		private const val GAP = 6

		private const val BAR = 0xDD111122.toInt()
		private const val SCORE_BOX = 0x22FFFFFF
		private const val TIMER = 0xFFFFD700.toInt()
		private const val RED = 0xFFFF4444.toInt()
		private const val CYAN = 0xFF44CCFF.toInt()
		private const val WHITE = 0xFFFFFFFF.toInt()
		private const val DIM = 0xFF777777.toInt()
	}
}
