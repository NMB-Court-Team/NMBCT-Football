package net.astrorbits.football.client.render

import net.astrorbits.football.match.TeamSide
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import kotlin.math.min

/** 电竞风格上中 Banner：半透明面板 + 左侧色条 + 分层字号与进出场动画。 */
object MatchEventBanner {

    data class Line(
        val text: String,
        val color: Int,
        val bold: Boolean = false,
        val scale: Float = 1f,
    )

    data class ScoreRow(
        val nameA: String,
        val scoreA: Int,
        val nameB: String,
        val scoreB: Int,
        val colorA: Int,
        val colorB: Int,
    )

    fun teamColor(side: TeamSide): Int = if (side == TeamSide.A) TEAM_A else TEAM_B

    fun render(
        extra: GuiGraphicsExtractor,
        font: Font,
        screenW: Int,
        screenH: Int,
        elapsedMs: Long,
        durationMs: Long,
        accentColor: Int,
        headline: Line?,
        lines: List<Line>,
        scoreRow: ScoreRow? = null,
    ) {
        val anim = computeAnim(elapsedMs, durationMs)
        val withAlpha = { color: Int -> applyAlpha(color, anim.alpha) }

        val contentW = measureContentWidth(font, headline, lines, scoreRow)
        val contentH = measureContentHeight(font, headline, lines, scoreRow)
        val panelW = ACCENT_W + PAD * 2 + contentW
        val panelH = PAD * 2 + contentH
        val panelX = (screenW - panelW) / 2
        val panelY = (screenH * TOP_FRAC).toInt() + anim.slidePx

        extra.fill(panelX, panelY, panelX + panelW, panelY + panelH, withAlpha(PANEL_BG))
        extra.fill(panelX, panelY, panelX + ACCENT_W, panelY + panelH, withAlpha(accentColor))
        extra.fill(panelX, panelY, panelX + panelW, panelY + 1, withAlpha(BORDER_TOP))
        extra.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, withAlpha(BORDER_TOP))

        val cx = panelX + ACCENT_W + PAD + contentW / 2
        var y = panelY + PAD

        headline?.let { line ->
            y += drawCenteredLine(extra, font, line, cx, y, withAlpha(line.color))
        }

        for (line in lines) {
            y += drawCenteredLine(extra, font, line, cx, y, withAlpha(line.color))
        }

        scoreRow?.let { row ->
            y += SCORE_TOP_GAP
            drawScoreRow(extra, font, cx, y, row, withAlpha)
            y += SCORE_ROW_H
        }
    }

    private data class Anim(val alpha: Float, val slidePx: Int)

    private fun computeAnim(elapsedMs: Long, durationMs: Long): Anim {
        val enterT = (elapsedMs.toFloat() / ENTER_MS).coerceIn(0f, 1f)
        val enter = easeOutCubic(enterT)
        val exitStart = (durationMs - EXIT_MS).coerceAtLeast(ENTER_MS)
        val exitT = if (elapsedMs >= exitStart) {
            ((elapsedMs - exitStart).toFloat() / EXIT_MS).coerceIn(0f, 1f)
        } else {
            0f
        }
        val exit = 1f - easeOutCubic(exitT)
        val alpha = min(enter, exit)
        val slidePx = ((1f - enter) * 10f).toInt()
        return Anim(alpha, slidePx)
    }

    private fun easeOutCubic(t: Float): Float {
        val u = 1f - t
        return 1f - u * u * u
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * ((color ushr 24) and 0xFF)).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0xFFFFFF)
    }

    private fun measureContentWidth(font: Font, headline: Line?, lines: List<Line>, scoreRow: ScoreRow?): Int {
        var w = 0
        headline?.let { w = maxOf(w, scaledWidth(font, it)) }
        for (line in lines) {
            w = maxOf(w, scaledWidth(font, line))
        }
        scoreRow?.let { w = maxOf(w, scoreRowWidth(font, it)) }
        return w
    }

    private fun measureContentHeight(font: Font, headline: Line?, lines: List<Line>, scoreRow: ScoreRow?): Int {
        var h = 0
        headline?.let { h += lineBlockHeight(font, it) }
        for (line in lines) {
            h += lineBlockHeight(font, line)
        }
        scoreRow?.let { h += SCORE_TOP_GAP + SCORE_ROW_H }
        return h
    }

    private fun lineBlockHeight(font: Font, line: Line): Int =
        (font.lineHeight * line.scale).toInt() + LINE_GAP

    private fun scaledWidth(font: Font, line: Line): Int =
        (font.width(toSequence(font, line.text, line.bold)) * line.scale).toInt()

    private fun scoreRowWidth(font: Font, row: ScoreRow): Int {
        val gap = font.width(" ")
        val dash = font.width(SEP)
        return font.width(row.nameA) + gap + SCORE_BOX_W + gap + dash + gap +
            SCORE_BOX_W + gap + font.width(row.nameB)
    }

    private fun drawCenteredLine(
        extra: GuiGraphicsExtractor,
        font: Font,
        line: Line,
        cx: Int,
        y: Int,
        color: Int,
    ): Int {
        val seq = toSequence(font, line.text, line.bold)
        val w = font.width(seq)
        val h = (font.lineHeight * line.scale).toInt()
        val pose = extra.pose()
        pose.pushMatrix()
        pose.translate(cx - w * line.scale / 2f, y.toFloat())
        pose.scale(line.scale, line.scale)
        extra.text(font, seq, 0, 0, color, true)
        pose.popMatrix()
        return h + LINE_GAP
    }

    private fun drawScoreRow(
        extra: GuiGraphicsExtractor,
        font: Font,
        cx: Int,
        y: Int,
        row: ScoreRow,
        withAlpha: (Int) -> Int,
    ) {
        val scoreA = row.scoreA.toString()
        val scoreB = row.scoreB.toString()
        val gap = font.width(" ")
        val totalW = scoreRowWidth(font, row)
        var x = cx - totalW / 2

        extra.text(font, row.nameA, x, y + 2, withAlpha(row.colorA), true)
        x += font.width(row.nameA) + gap

        drawScoreBox(extra, font, scoreA, x, y, withAlpha)
        x += SCORE_BOX_W + gap

        extra.text(font, SEP, x, y + 2, withAlpha(DIM), true)
        x += font.width(SEP) + gap

        drawScoreBox(extra, font, scoreB, x, y, withAlpha)
        x += SCORE_BOX_W + gap

        extra.text(font, row.nameB, x, y + 2, withAlpha(row.colorB), true)
    }

    private fun drawScoreBox(
        extra: GuiGraphicsExtractor,
        font: Font,
        text: String,
        x: Int,
        y: Int,
        withAlpha: (Int) -> Int,
    ) {
        extra.fill(x, y, x + SCORE_BOX_W, y + SCORE_ROW_H, withAlpha(SCORE_BOX_BG))
        val seq = Component.literal(text).visualOrderText
        extra.text(font, seq, x + SCORE_BOX_W / 2 - font.width(seq) / 2, y + 2, withAlpha(WHITE), true)
    }

    private fun toSequence(font: Font, text: String, bold: Boolean): FormattedCharSequence {
        val component = if (bold) {
            Component.literal(text).withStyle(Style.EMPTY.withBold(true))
        } else {
            Component.literal(text)
        }
        return component.visualOrderText
    }

    private const val SEP = "-"
    private const val TOP_FRAC = 0.12f
    private const val ENTER_MS = 250L
    private const val EXIT_MS = 700L
    private const val PAD = 10
    private const val ACCENT_W = 3
    private const val LINE_GAP = 3
    private const val SCORE_TOP_GAP = 6
    private const val SCORE_ROW_H = 16
    private const val SCORE_BOX_W = 22

    private const val PANEL_BG = 0xE0111828.toInt()
    private const val BORDER_TOP = 0x55FFFFFF
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val DIM = 0xFF777777.toInt()
    private const val SCORE_BOX_BG = 0x22FFFFFF
    private const val TEAM_A = 0xFFFF4444.toInt()
    private const val TEAM_B = 0xFF44CCFF.toInt()

    const val ACCENT_GOLD = 0xFFFFD700.toInt()
    const val ACCENT_WIN = 0xFFFFAA00.toInt()
    const val ACCENT_LOSS = 0xFFFF55AA.toInt()
    const val ACCENT_DRAW = 0xFF55FF55.toInt()
}
