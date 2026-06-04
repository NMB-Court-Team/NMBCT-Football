package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.MatchHudTeams
import net.astrorbits.football.client.match.PenaltyShootoutClient
import net.astrorbits.football.client.util.FootballHudVisibility
import net.astrorbits.football.match.MatchPhase
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
        val client = Minecraft.getInstance()
        if (FootballHudVisibility.isDebugOverlayOpen(client)) {
            return
        }

        val font = client.font
        val mainPhase = MatchState.getMainDisplayPhase()
        val phaseText = Component.translatable(mainPhase.displayNameKey)
        val timer = MatchState.formatElapsed(MatchState.getPhaseDisplayTicks())
        val teamA = MatchState.teamAName.visualOrderText
        val teamB = MatchState.teamBName.visualOrderText
        val scoreA = Component.literal(MatchState.teamAScore.toString()).visualOrderText
        val scoreB = Component.literal(MatchState.teamBScore.toString()).visualOrderText

        val inPrep = MatchState.isPreMatchPreparationPhase()
        val panelW = PAD + P_W + GAP + T_W + GAP + NAME_W + GAP + S_W + GAP + D_W + GAP + S_W + GAP + NAME_W + PAD
        val cy = Y + H / 2 - font.lineHeight / 2
        val phaseColor = if (inPrep) PHASE_PREP else PHASE
        val barColor = if (inPrep) BAR_PREP else BAR

        // ── 主栏 ──
        extra.fill(X, Y, X + panelW, Y + H, barColor)

        var cx = X + PAD
        extra.text(font, phaseText, cx, cy, phaseColor, true)
        cx += P_W + GAP
        val timerX = cx
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

        // ── 点球大战：本轮信息 + 点球比分（比分在信息行下方）──
        if (MatchState.currentPhase == MatchPhase.PENALTIES && PenaltyShootoutClient.active) {
            val penBaseY = if (MatchState.isStoppagePhase()) ST_Y + ST_H + 4 else Y + H + 4
            val infoY = penBaseY
            val scoreY = penBaseY + PEN_INFO_H + PEN_ROW_GAP

            val goalTeam = MatchHudTeams.name(PenaltyShootoutClient.activeDefendingTeam)
            val kickTeam = MatchHudTeams.name(PenaltyShootoutClient.currentKickerTeam)
            val kicker = PenaltyShootoutClient.kickerName.ifBlank { "?" }

            val goalLabel = Component.translatable("hud.nmbct-football.penalty.goal_team").visualOrderText
            val kickLabel = Component.translatable("hud.nmbct-football.penalty.kicking_team").visualOrderText
            val kickerLabel = Component.translatable("hud.nmbct-football.penalty.kicker").visualOrderText
            val goalValue = Component.literal(goalTeam).visualOrderText
            val kickValue = Component.literal(kickTeam).visualOrderText
            val kickerValue = Component.literal(kicker).visualOrderText

            val infoPanelW = measurePenaltyInfoWidth(
                font,
                goalLabel,
                goalValue,
                kickLabel,
                kickValue,
                kickerLabel,
                kickerValue,
            )
            val infoCy = infoY + PEN_INFO_H / 2 - font.lineHeight / 2
            extra.fill(X, infoY, X + infoPanelW, infoY + PEN_INFO_H, PEN_BAR)
            var infoCx = X + PEN_PAD
            infoCx += drawLabelValue(
                extra,
                font,
                infoCx,
                infoCy,
                goalLabel,
                goalValue,
                MatchEventBanner.teamColor(PenaltyShootoutClient.activeDefendingTeam),
            )
            infoCx += PEN_SEGMENT_GAP
            infoCx += drawLabelValue(
                extra,
                font,
                infoCx,
                infoCy,
                kickLabel,
                kickValue,
                MatchEventBanner.teamColor(PenaltyShootoutClient.currentKickerTeam),
            )
            infoCx += PEN_SEGMENT_GAP
            drawLabelValue(
                extra,
                font,
                infoCx,
                infoCy,
                kickerLabel,
                kickerValue,
                MatchEventBanner.teamColor(PenaltyShootoutClient.currentKickerTeam),
            )

            val penLabel = Component.translatable("hud.nmbct-football.penalty.score_label").visualOrderText
            val penA = Component.literal(PenaltyShootoutClient.penaltyScoreA.toString()).visualOrderText
            val penB = Component.literal(PenaltyShootoutClient.penaltyScoreB.toString()).visualOrderText
            val penPanelW = PEN_PAD + font.width(penLabel) + PEN_GAP + S_W + PEN_GAP + D_W + PEN_GAP + S_W + PEN_PAD
            val penCy = scoreY + PEN_H / 2 - font.lineHeight / 2
            extra.fill(X, scoreY, X + penPanelW, scoreY + PEN_H, PEN_BAR)
            var penCx = X + PEN_PAD
            extra.text(font, penLabel, penCx, penCy, PEN_TEXT, true)
            penCx += font.width(penLabel) + PEN_GAP
            penCx += drawScore(extra, font, penA, penCx, penCy, scoreY, PEN_H)
            penCx += PEN_GAP
            extra.text(font, SEP, penCx + D_W / 2 - font.width(SEP) / 2, penCy, DIM, true)
            penCx += D_W + PEN_GAP
            drawScore(extra, font, penB, penCx, penCy, scoreY, PEN_H)
        }

        // ── 补时面板 ──
        if (MatchState.isStoppagePhase()) {
            val stPhase = Component.translatable("hud.nmbct-football.stoppage")
            val stTimer = MatchState.formatStoppageWithTarget()
            val stPanelW = ST_PAD + ST_P_W + ST_GAP + ST_T_W + ST_PAD
            val stCy = ST_Y + ST_H / 2 - font.lineHeight / 2

            extra.fill(X, ST_Y, X + stPanelW, ST_Y + ST_H, ST_BAR)

            var stCx = X + ST_PAD
            extra.text(font, stPhase, stCx, stCy, ST_TEXT, true)
            stCx += ST_P_W + ST_GAP
            extra.text(font, stTimer, stCx, stCy, ST_TIMER, true)
        }

        // ── 阶段终止时间 ──
        if (MatchState.getPhaseTargetTicks() > 0) {
            val endTime = MatchState.formatPhaseEndTime()
            val scale = 0.7f
            extra.pose().pushMatrix()
            extra.pose().scale(scale, scale)
            extra.text(font, endTime, (timerX / scale).toInt(), ((cy + font.lineHeight) / scale).toInt(), END_TIME, true)
            extra.pose().popMatrix()
        }
    }

    private fun drawName(extra: GuiGraphicsExtractor, font: Font, text: FormattedCharSequence, x: Int, y: Int, color: Int, rightAlign: Boolean): Int {
        val w = font.width(text)
        val tx = if (rightAlign) x + NAME_W - w else x
        extra.text(font, text, tx, y, color, true)
        return NAME_W
    }

    private fun measurePenaltyInfoWidth(
        font: Font,
        goalLabel: FormattedCharSequence,
        goalValue: FormattedCharSequence,
        kickLabel: FormattedCharSequence,
        kickValue: FormattedCharSequence,
        kickerLabel: FormattedCharSequence,
        kickerValue: FormattedCharSequence,
    ): Int {
        fun fieldW(label: FormattedCharSequence, value: FormattedCharSequence) =
            font.width(label) + PEN_FIELD_GAP + font.width(value)
        return PEN_PAD * 2 +
            fieldW(goalLabel, goalValue) + PEN_SEGMENT_GAP +
            fieldW(kickLabel, kickValue) + PEN_SEGMENT_GAP +
            fieldW(kickerLabel, kickerValue)
    }

    private fun drawLabelValue(
        extra: GuiGraphicsExtractor,
        font: Font,
        x: Int,
        y: Int,
        label: FormattedCharSequence,
        value: FormattedCharSequence,
        valueColor: Int,
    ): Int {
        extra.text(font, label, x, y, PEN_LABEL, true)
        val valueX = x + font.width(label) + PEN_FIELD_GAP
        extra.text(font, value, valueX, y, valueColor, true)
        return font.width(label) + PEN_FIELD_GAP + font.width(value)
    }

    private fun drawScore(
        extra: GuiGraphicsExtractor,
        font: Font,
        text: FormattedCharSequence,
        x: Int,
        y: Int,
        panelTop: Int = Y,
        panelHeight: Int = H,
    ): Int {
        extra.fill(x, panelTop + 3, x + S_W, panelTop + panelHeight - 3, SCORE_BOX)
        extra.text(font, text, x + S_W / 2 - font.width(text) / 2, y, WHITE, true)
        return S_W
    }

    companion object {
        private const val SEP = "-"

        private const val X = 8; private const val Y = 8; private const val H = 22
        private const val PAD = 10
        private const val P_W = 48; private const val T_W = 42; private const val NAME_W = 48
        private const val S_W = 26; private const val D_W = 14
        private const val GAP = 6

        // 补时面板
        private const val ST_Y = 32; private const val ST_H = 18
        private const val ST_PAD = 10; private const val ST_P_W = 24; private const val ST_T_W = 90; private const val ST_GAP = 6

        private const val PEN_INFO_H = 18
        private const val PEN_H = 18
        private const val PEN_ROW_GAP = 4
        private const val PEN_PAD = 10
        private const val PEN_GAP = 6
        private const val PEN_FIELD_GAP = 4
        private const val PEN_SEGMENT_GAP = 12
        private const val PEN_BAR = 0xDD221133.toInt()
        private const val PEN_LABEL = 0xFFAAAAAA.toInt()
        private const val PEN_TEXT = 0xFFFFAA44.toInt()

        // 颜色
        private const val BAR = 0xDD111122.toInt()
        private const val BAR_PREP = 0xDD142818.toInt()
        private const val SCORE_BOX = 0x22FFFFFF
        private const val PHASE = 0xFFCCCCCC.toInt()
        private const val PHASE_PREP = MatchEventBanner.ACCENT_PREP
        private const val TIMER = 0xFFFFD700.toInt()
        private const val RED = 0xFFFF4444.toInt()
        private const val CYAN = 0xFF44CCFF.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val DIM = 0xFF777777.toInt()
        private const val ST_BAR = 0xDD111133.toInt()
        private const val ST_TEXT = 0xFFCCCCCC.toInt()
        private const val ST_TIMER = 0xFFFFAA44.toInt()
        private const val END_TIME = 0xFFAAAAAA.toInt()
    }
}