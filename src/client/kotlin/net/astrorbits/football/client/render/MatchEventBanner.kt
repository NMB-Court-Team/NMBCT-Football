package net.astrorbits.football.client.render

import net.astrorbits.football.match.TeamSide
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import kotlin.math.max
import kotlin.math.min

/**
 * 电竞风格事件 Banner。进球 / 出界 / 半场 / 结算 使用截然不同的版式与动效；
 * [renderDefault] 供开场等轻量提示使用。
 */
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

    /** 进球：宽卡片 + 顶底金色光带 + 大号标题 + 突出比分 */
    fun renderGoal(
        extra: GuiGraphicsExtractor,
        font: Font,
        screenW: Int,
        screenH: Int,
        elapsedMs: Long,
        durationMs: Long,
        ownGoal: Boolean,
        headline: String,
        teamLine: String,
        teamColor: Int,
        scorerLine: String,
        scoreRow: ScoreRow,
    ) {
        val anim = computeAnim(elapsedMs, durationMs, enterMs = 320L, exitMs = 800L, slidePx = 14)
        val withAlpha = { color: Int -> applyAlpha(color, anim.alpha) }
        val pop = 1f + (1f - easeOutCubic((elapsedMs / 380f).coerceIn(0f, 1f))) * 0.12f

        val headlineLine = Line(headline, if (ownGoal) ACCENT_OWN_GOAL else ACCENT_GOLD, bold = true, scale = 2.25f * pop)
        val contentW = max(
            GOAL_MIN_W,
            maxOf(
                scaledWidth(font, headlineLine),
                scaledWidth(font, Line(teamLine, teamColor, bold = true)),
                scaledWidth(font, Line(scorerLine, WHITE, bold = true)),
                scoreRowWidth(font, scoreRow, SCORE_BOX_GOAL, SCORE_GAP_GOAL),
            ),
        )
        val contentH = lineBlockHeight(font, headlineLine) +
            lineBlockHeight(font, Line(teamLine, teamColor, bold = true)) +
            lineBlockHeight(font, Line(scorerLine, WHITE, bold = true)) +
            SCORE_TOP_GAP_GOAL + SCORE_ROW_GOAL
        val panelW = contentW + PAD_GOAL * 2
        val panelH = PAD_GOAL * 2 + contentH + GOAL_BAR_H * 2
        val panelX = (screenW - panelW) / 2
        val panelY = (screenH * 0.09f).toInt() + anim.slidePx

        val gold = if (ownGoal) ACCENT_OWN_GOAL else ACCENT_GOLD
        extra.fill(panelX, panelY, panelX + panelW, panelY + panelH, withAlpha(GOAL_PANEL_BG))
        extra.fill(panelX, panelY, panelX + panelW, panelY + GOAL_BAR_H, withAlpha(gold))
        extra.fill(panelX, panelY + panelH - GOAL_BAR_H, panelX + panelW, panelY + panelH, withAlpha(applyAlpha(gold, 0.45f)))
        extra.fill(panelX, panelY + GOAL_BAR_H, panelX + panelW, panelY + GOAL_BAR_H + 1, withAlpha(GOAL_GLOW_LINE))

        val cx = panelX + panelW / 2
        var y = panelY + GOAL_BAR_H + PAD_GOAL
        y += drawCenteredLine(extra, font, headlineLine, cx, y, withAlpha(headlineLine.color))
        y += drawCenteredLine(extra, font, Line(teamLine, teamColor, bold = true), cx, y, withAlpha(teamColor))
        y += drawCenteredLine(extra, font, Line(scorerLine, WHITE, bold = true), cx, y, withAlpha(WHITE))
        y += SCORE_TOP_GAP_GOAL
        drawScoreRow(extra, font, cx, y, scoreRow, withAlpha, SCORE_BOX_GOAL, SCORE_ROW_GOAL, SCORE_GAP_GOAL)
    }

    /** 出界：上中醒目卡片（避开右侧键位提示），类型 + 提出者 + 发球方 */
    fun renderOut(
        extra: GuiGraphicsExtractor,
        font: Font,
        screenW: Int,
        screenH: Int,
        elapsedMs: Long,
        durationMs: Long,
        typeText: String,
        typeColor: Int,
        touchLine: String,
        touchColor: Int,
        restartLine: String,
        restartColor: Int,
    ) {
        val anim = computeAnim(elapsedMs, durationMs, enterMs = 260L, exitMs = 650L, slidePx = 12)
        val withAlpha = { color: Int -> applyAlpha(color, anim.alpha) }

        val headline = Line(typeText, typeColor, bold = true, scale = 1.85f)
        val touch = Line(touchLine, touchColor)
        val restart = Line(restartLine, restartColor, bold = true)
        val contentW = max(OUT_MIN_W, maxOf(scaledWidth(font, headline), scaledWidth(font, touch), scaledWidth(font, restart)))
        val contentH = lineBlockHeight(font, headline) + lineBlockHeight(font, touch) + lineBlockHeight(font, restart)
        val panelW = ACCENT_W + OUT_PAD * 2 + contentW
        val panelH = OUT_TOP_BAR + OUT_PAD * 2 + contentH
        val panelX = (screenW - panelW) / 2
        val panelY = (screenH * 0.11f).toInt() + anim.slidePx

        extra.fill(panelX, panelY, panelX + panelW, panelY + panelH, withAlpha(OUT_PANEL_BG))
        extra.fill(panelX, panelY, panelX + panelW, panelY + OUT_TOP_BAR, withAlpha(typeColor))
        extra.fill(panelX, panelY, panelX + ACCENT_W, panelY + panelH, withAlpha(typeColor))

        val cx = panelX + ACCENT_W + OUT_PAD + contentW / 2
        var y = panelY + OUT_TOP_BAR + OUT_PAD
        y += drawCenteredLine(extra, font, headline, cx, y, withAlpha(typeColor))
        y += drawCenteredLine(extra, font, touch, cx, y, withAlpha(touchColor))
        drawCenteredLine(extra, font, restart, cx, y, withAlpha(restartColor))
    }

    /** 半场：上中窄条，单行「阶段 · 发球方」 */
    fun renderHalf(
        extra: GuiGraphicsExtractor,
        font: Font,
        screenW: Int,
        screenH: Int,
        elapsedMs: Long,
        durationMs: Long,
        combinedText: String,
        phaseColor: Int,
        kickoffColor: Int,
    ) {
        val anim = computeAnim(elapsedMs, durationMs, enterMs = 220L, exitMs = 550L, slidePx = 8)
        val withAlpha = { color: Int -> applyAlpha(color, anim.alpha) }

        val sep = " · "
        val sepW = font.width(sep)
        val parts = combinedText.split(" · ", limit = 2)
        val phasePart = parts.getOrElse(0) { combinedText }
        val kickoffPart = parts.getOrElse(1) { "" }
        val contentW = font.width(toSequence(font, phasePart, bold = true)) + sepW +
            if (kickoffPart.isEmpty()) 0 else font.width(toSequence(font, kickoffPart, bold = true))
        val panelW = max(HALF_MIN_W, ACCENT_W + HALF_PAD * 2 + contentW)
        val panelH = HALF_TOP_BAR + HALF_PAD * 2 + font.lineHeight
        val panelX = (screenW - panelW) / 2
        val panelY = (screenH * 0.09f).toInt() + anim.slidePx

        extra.fill(panelX, panelY, panelX + panelW, panelY + panelH, withAlpha(HALF_PANEL_BG))
        extra.fill(panelX, panelY, panelX + panelW, panelY + HALF_TOP_BAR, withAlpha(HALF_ACCENT))
        extra.fill(panelX, panelY, panelX + ACCENT_W, panelY + panelH, withAlpha(HALF_ACCENT))

        val textY = panelY + HALF_TOP_BAR + HALF_PAD
        var x = panelX + (panelW - contentW) / 2
        val phaseSeq = toSequence(font, phasePart, bold = true)
        extra.text(font, phaseSeq, x, textY, withAlpha(phaseColor), true)
        x += font.width(phaseSeq)
        if (kickoffPart.isNotEmpty()) {
            extra.text(font, sep, x, textY, withAlpha(HALF_SEP), true)
            x += sepW
            val kickSeq = toSequence(font, kickoffPart, bold = true)
            extra.text(font, kickSeq, x, textY, withAlpha(kickoffColor), true)
        }
    }

    /** 结算：中下宽牌，顶底粗色带 + 超大胜负标题 + 大号比分 */
    fun renderResult(
        extra: GuiGraphicsExtractor,
        font: Font,
        screenW: Int,
        screenH: Int,
        elapsedMs: Long,
        durationMs: Long,
        resultText: String,
        resultColor: Int,
        accentColor: Int,
        scoreRow: ScoreRow,
    ) {
        val anim = computeAnim(elapsedMs, durationMs, enterMs = 450L, exitMs = 900L, slidePx = 0)
        val withAlpha = { color: Int -> applyAlpha(color, anim.alpha) }
        val headlineLine = Line(resultText, resultColor, bold = true, scale = 2.5f)
        val scoreW = scoreRowWidth(font, scoreRow, SCORE_BOX_RESULT, SCORE_GAP_RESULT, namesBold = true)
        val contentW = max(RESULT_MIN_W, maxOf(scaledWidth(font, headlineLine), scoreW))
        val contentH = lineBlockHeight(font, headlineLine) + SCORE_TOP_GAP_RESULT + SCORE_ROW_RESULT
        val panelW = contentW + PAD_RESULT * 2
        val panelH = PAD_RESULT * 2 + contentH + RESULT_BAR_H * 2
        val panelX = (screenW - panelW) / 2
        val panelY = (screenH * 0.20f).toInt()

        extra.fill(panelX, panelY, panelX + panelW, panelY + panelH, withAlpha(RESULT_PANEL_BG))
        extra.fill(panelX, panelY, panelX + panelW, panelY + RESULT_BAR_H, withAlpha(accentColor))
        extra.fill(panelX, panelY + panelH - RESULT_BAR_H, panelX + panelW, panelY + panelH, withAlpha(accentColor))

        val cx = panelX + panelW / 2
        var y = panelY + RESULT_BAR_H + PAD_RESULT
        y += drawCenteredLine(extra, font, headlineLine, cx, y, withAlpha(resultColor))
        y += SCORE_TOP_GAP_RESULT
        drawScoreRow(
            extra, font, cx, y, scoreRow, withAlpha,
            SCORE_BOX_RESULT, SCORE_ROW_RESULT, SCORE_GAP_RESULT,
            scoreScale = 1.2f,
            namesBold = true,
        )
    }

    /** 开场等：通用左色条卡片 */
    fun renderDefault(
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
        val anim = computeAnim(elapsedMs, durationMs, enterMs = ENTER_MS, exitMs = EXIT_MS, slidePx = 10)
        val withAlpha = { color: Int -> applyAlpha(color, anim.alpha) }

        val contentW = measureContentWidth(font, headline, lines, scoreRow, SCORE_BOX_DEFAULT, SCORE_GAP_DEFAULT)
        val contentH = measureContentHeight(font, headline, lines, scoreRow, SCORE_TOP_GAP_DEFAULT, SCORE_ROW_DEFAULT)
        val panelW = ACCENT_W + PAD_DEFAULT * 2 + contentW
        val panelH = PAD_DEFAULT * 2 + contentH
        val panelX = (screenW - panelW) / 2
        val panelY = (screenH * TOP_FRAC_DEFAULT).toInt() + anim.slidePx

        extra.fill(panelX, panelY, panelX + panelW, panelY + panelH, withAlpha(PANEL_BG))
        extra.fill(panelX, panelY, panelX + ACCENT_W, panelY + panelH, withAlpha(accentColor))
        extra.fill(panelX, panelY, panelX + panelW, panelY + 1, withAlpha(BORDER_TOP))
        extra.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, withAlpha(BORDER_TOP))

        val cx = panelX + ACCENT_W + PAD_DEFAULT + contentW / 2
        var y = panelY + PAD_DEFAULT
        headline?.let { y += drawCenteredLine(extra, font, it, cx, y, withAlpha(it.color)) }
        for (line in lines) {
            y += drawCenteredLine(extra, font, line, cx, y, withAlpha(line.color))
        }
        scoreRow?.let {
            y += SCORE_TOP_GAP_DEFAULT
            drawScoreRow(extra, font, cx, y, it, withAlpha, SCORE_BOX_DEFAULT, SCORE_ROW_DEFAULT, SCORE_GAP_DEFAULT)
        }
    }

    private data class Anim(val alpha: Float, val slidePx: Int)

    private fun computeAnim(elapsedMs: Long, durationMs: Long, enterMs: Long, exitMs: Long, slidePx: Int): Anim {
        val enter = easeOutCubic((elapsedMs.toFloat() / enterMs).coerceIn(0f, 1f))
        val exitStart = (durationMs - exitMs).coerceAtLeast(enterMs)
        val exitT = if (elapsedMs >= exitStart) {
            ((elapsedMs - exitStart).toFloat() / exitMs).coerceIn(0f, 1f)
        } else {
            0f
        }
        val alpha = min(enter, 1f - easeOutCubic(exitT))
        return Anim(alpha, ((1f - enter) * slidePx).toInt())
    }

    private fun easeOutCubic(t: Float): Float {
        val u = 1f - t
        return 1f - u * u * u
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val baseA = (color ushr 24) and 0xFF
        val a = if (baseA == 0) {
            (alpha * 255).toInt()
        } else {
            (alpha * baseA).toInt()
        }.coerceIn(0, 255)
        return (a shl 24) or (color and 0xFFFFFF)
    }

    private fun measureContentWidth(
        font: Font,
        headline: Line?,
        lines: List<Line>,
        scoreRow: ScoreRow?,
        scoreBoxW: Int,
        scoreGap: Int,
    ): Int {
        var w = 0
        headline?.let { w = maxOf(w, scaledWidth(font, it)) }
        for (line in lines) {
            w = maxOf(w, scaledWidth(font, line))
        }
        scoreRow?.let { w = maxOf(w, scoreRowWidth(font, it, scoreBoxW, scoreGap)) }
        return w
    }

    private fun measureContentHeight(
        font: Font,
        headline: Line?,
        lines: List<Line>,
        scoreRow: ScoreRow?,
        scoreTopGap: Int,
        scoreRowH: Int,
    ): Int {
        var h = 0
        headline?.let { h += lineBlockHeight(font, it) }
        for (line in lines) {
            h += lineBlockHeight(font, line)
        }
        scoreRow?.let { h += scoreTopGap + scoreRowH }
        return h
    }

    private fun lineBlockHeight(font: Font, line: Line): Int =
        (font.lineHeight * line.scale).toInt() + LINE_GAP

    private fun scaledWidth(font: Font, line: Line): Int =
        (font.width(toSequence(font, line.text, line.bold)) * line.scale).toInt()

    private fun scoreRowWidth(font: Font, row: ScoreRow, scoreBoxW: Int, gapExtra: Int, namesBold: Boolean = false): Int {
        val gap = font.width(" ") + gapExtra
        return font.width(toSequence(font, row.nameA, namesBold)) + gap + scoreBoxW + gap + font.width(SEP) + gap +
            scoreBoxW + gap + font.width(toSequence(font, row.nameB, namesBold))
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
        scoreBoxW: Int,
        scoreRowH: Int,
        gapExtra: Int,
        scoreScale: Float = 1f,
        namesBold: Boolean = false,
    ) {
        val scoreA = row.scoreA.toString()
        val scoreB = row.scoreB.toString()
        val gap = font.width(" ") + gapExtra
        val totalW = scoreRowWidth(font, row, scoreBoxW, gapExtra, namesBold)
        var x = cx - totalW / 2

        val rowTextH = max(font.lineHeight.toFloat(), font.lineHeight * scoreScale)
        val nameY = y + ((scoreRowH - rowTextH) / 2f).toInt()

        val nameASeq = toSequence(font, row.nameA, namesBold)
        extra.text(font, nameASeq, x, nameY, withAlpha(row.colorA), true)
        x += font.width(nameASeq) + gap

        drawScoreBox(extra, font, scoreA, x, y, withAlpha, scoreBoxW, scoreRowH, scoreScale)
        x += scoreBoxW + gap

        extra.text(font, SEP, x, nameY, withAlpha(DIM), true)
        x += font.width(SEP) + gap

        drawScoreBox(extra, font, scoreB, x, y, withAlpha, scoreBoxW, scoreRowH, scoreScale)
        x += scoreBoxW + gap

        val nameBSeq = toSequence(font, row.nameB, namesBold)
        extra.text(font, nameBSeq, x, nameY, withAlpha(row.colorB), true)
    }

    private fun drawScoreBox(
        extra: GuiGraphicsExtractor,
        font: Font,
        text: String,
        x: Int,
        y: Int,
        withAlpha: (Int) -> Int,
        boxW: Int,
        boxH: Int,
        scoreScale: Float,
    ) {
        extra.fill(x, y, x + boxW, y + boxH, withAlpha(SCORE_BOX_BG))
        val seq = Component.literal(text).withStyle(Style.EMPTY.withBold(true)).visualOrderText
        val pose = extra.pose()
        pose.pushMatrix()
        val tx = x + boxW / 2f - font.width(seq) * scoreScale / 2f
        val ty = y + (boxH - font.lineHeight * scoreScale) / 2f
        pose.translate(tx, ty)
        pose.scale(scoreScale, scoreScale)
        extra.text(font, seq, 0, 0, withAlpha(WHITE), true)
        pose.popMatrix()
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
    private const val LINE_GAP = 3

    private const val ENTER_MS = 250L
    private const val EXIT_MS = 700L
    private const val TOP_FRAC_DEFAULT = 0.12f
    private const val PAD_DEFAULT = 10
    private const val ACCENT_W = 3
    private const val SCORE_TOP_GAP_DEFAULT = 6
    private const val SCORE_ROW_DEFAULT = 16
    private const val SCORE_BOX_DEFAULT = 22
    private const val SCORE_GAP_DEFAULT = 0

    private const val GOAL_MIN_W = 200
    private const val PAD_GOAL = 14
    private const val GOAL_BAR_H = 4
    private const val GOAL_PANEL_BG = 0xE0181408.toInt()
    private const val GOAL_GLOW_LINE = 0x66FFD700
    private const val SCORE_TOP_GAP_GOAL = 8
    private const val SCORE_ROW_GOAL = 20
    private const val SCORE_BOX_GOAL = 28
    private const val SCORE_GAP_GOAL = 2

    private const val OUT_MIN_W = 180
    private const val OUT_PAD = 12
    private const val OUT_TOP_BAR = 4
    private const val OUT_PANEL_BG = 0xEE121820.toInt()

    private const val HALF_MIN_W = 140
    private const val HALF_PAD = 10
    private const val HALF_TOP_BAR = 3
    private const val HALF_PANEL_BG = 0xDD1A1A2E.toInt()
    private const val HALF_ACCENT = 0xFF8899BB.toInt()
    private const val HALF_SEP = 0xFF888888.toInt()

    private const val RESULT_MIN_W = 240
    private const val PAD_RESULT = 16
    private const val RESULT_BAR_H = 5
    private const val RESULT_PANEL_BG = 0xF0101018.toInt()
    private const val SCORE_TOP_GAP_RESULT = 10
    private const val SCORE_ROW_RESULT = 24
    private const val SCORE_BOX_RESULT = 34
    private const val SCORE_GAP_RESULT = 4

    private const val PANEL_BG = 0xE0111828.toInt()
    private const val BORDER_TOP = 0x55FFFFFF
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val DIM = 0xFF777777.toInt()
    private const val SCORE_BOX_BG = 0x22FFFFFF
    private const val TEAM_A = 0xFFFF4444.toInt()
    private const val TEAM_B = 0xFF44CCFF.toInt()

    const val ACCENT_GOLD = 0xFFFFD700.toInt()
    const val ACCENT_OWN_GOAL = 0xFFCC66FF.toInt()
    const val ACCENT_WIN = 0xFFFFAA00.toInt()
    const val ACCENT_LOSS = 0xFFFF55AA.toInt()
    const val ACCENT_DRAW = 0xFF55FF55.toInt()
}
