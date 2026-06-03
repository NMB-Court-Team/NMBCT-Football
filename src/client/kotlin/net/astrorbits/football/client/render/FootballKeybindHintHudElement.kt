package net.astrorbits.football.client.render

import net.astrorbits.football.client.FootballOperabilityClient
import net.astrorbits.football.client.StaminaClient
import net.astrorbits.football.client.util.FootballHudVisibility
import net.astrorbits.football.client.GoalkeeperStateClient
import net.astrorbits.football.client.key.FootballInputHandler
import net.astrorbits.football.client.key.FootballKeyBindings
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level

class FootballKeybindHintHudElement : HudElement {
    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        val client = Minecraft.getInstance()
        if (!canRenderHud(client)) {
            return
        }
        val player = client.player ?: return
        val level = client.level ?: return

        val font = client.font
        val lookAroundSection = buildLookAroundSection(font, player)
        val footballRows = if (FootballOperabilityClient.canShowFootballHints(player)) {
            buildFootballHintRows(font, player, level)
        } else {
            emptyList()
        }

        if (footballRows.isEmpty()) {
            renderPanel(
                extra = extra,
                font = font,
                screenW = client.window.guiScaledWidth,
                title = null,
                titleColor = TITLE_COLOR_ACTIVE,
                rows = lookAroundSection,
            )
            return
        }

        val titleKey = if (GoalkeeperStateClient.isGoalkeeper) TITLE_KEY_GK else TITLE_KEY
        val titleColor = if (footballRows.any { it.colors == RowColors.ACTIVE }) {
            RowColors.ACTIVE.titleColor
        } else {
            RowColors.INACTIVE.titleColor
        }
        val styledRows = footballRows + lookAroundSection
        renderPanel(
            extra = extra,
            font = font,
            screenW = client.window.guiScaledWidth,
            title = Component.translatable(titleKey).string,
            titleColor = titleColor,
            rows = styledRows,
        )
    }

    private fun canRenderHud(client: Minecraft): Boolean {
        if (client.isPaused || FootballHudVisibility.isDebugOverlayOpen(client)) {
            return false
        }
        if (client.player == null || client.level == null) {
            return false
        }
        val screen = client.screen
        return screen == null || screen is ChatScreen
    }

    private fun buildLookAroundSection(font: Font, player: LocalPlayer): List<StyledRow> {
        val rows = mutableListOf<StyledRow>()
        if (!GoalkeeperStateClient.isGoalkeeper) {
            val nowTick = player.level()?.gameTime ?: 0L
            rows += StyledRow(
                buildHintRow(font, FootballKeyBindings.SLIDE_TACKLE, SLIDE_TACKLE_LABEL_KEY),
                if (FootballInputHandler.canSlideTackle(nowTick)) RowColors.ACTIVE else RowColors.INACTIVE,
            )
            rows += StyledRow(
                buildHintRow(font, FootballKeyBindings.BOOST_SPRINT, BOOST_SPRINT_LABEL_KEY),
                if (player.isSprinting && StaminaClient.stamina > 0f) RowColors.ACTIVE else RowColors.INACTIVE,
            )
        }
        rows += StyledRow(
            buildHintRow(font, FootballKeyBindings.LOOK_AROUND, LOOK_AROUND_LABEL_KEY),
            RowColors.ACTIVE,
        )
        return rows
    }

    private fun buildFootballHintRows(font: Font, player: LocalPlayer, level: Level): List<StyledRow> {
        return buildFootballActionRows().map { (key, labelKey) ->
            val row = buildHintRow(font, key, labelKey)
            val colors = if (FootballOperabilityClient.canUseFootballHint(player, level, key)) {
                RowColors.ACTIVE
            } else {
                RowColors.INACTIVE
            }
            StyledRow(row, colors)
        }
    }

    private fun buildFootballActionRows(): List<Pair<KeyMapping, String>> {
        if (!GoalkeeperStateClient.isGoalkeeper) {
            return OUTFIELD_HINT_ROWS
        }
        return if (GoalkeeperStateClient.isHoldingBall) {
            GK_HOLDING_HINT_ROWS
        } else {
            val rows = GK_FREE_HINT_ROWS.toMutableList()
            if (FootballInputHandler.isGoalkeeperDiveChargeActive()) {
                rows += FootballKeyBindings.DRIBBLE to GK_DIVE_INTERRUPT_LABEL_KEY
            }
            rows
        }
    }

    private fun buildHintRow(font: Font, key: KeyMapping, labelKey: String): HintRow {
        val keyLabel = key.translatedKeyMessage.string
        return HintRow(
            keyLabel = keyLabel,
            label = Component.translatable(labelKey).string,
            keyBoxW = keyBoxWidth(font, keyLabel),
        )
    }

    private fun renderPanel(
        extra: GuiGraphicsExtractor,
        font: Font,
        screenW: Int,
        title: String?,
        titleColor: Int,
        rows: List<StyledRow>,
    ) {
        val rowContentW = rows.maxOf { font.width(it.row.label) + it.row.keyBoxW + KEY_LABEL_GAP }
        val titleW = title?.let(font::width) ?: 0
        val panelW = PAD * 2 + maxOf(titleW, rowContentW)
        val panelH = if (title == null) {
            PAD * 2 + rows.size * (font.lineHeight + ROW_GAP) - ROW_GAP
        } else {
            PAD + font.lineHeight + ROW_GAP + rows.size * (font.lineHeight + ROW_GAP) + PAD
        }
        val panelX = screenW - MARGIN - panelW
        val panelY = MARGIN

        extra.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG)
        var y = panelY + PAD
        if (title != null) {
            extra.text(font, title, panelX + panelW - PAD - font.width(title), y, titleColor, true)
            y += font.lineHeight + ROW_GAP
        }
        for (styledRow in rows) {
            renderRow(extra, font, panelX, panelW, y, styledRow.row, styledRow.colors)
            y += font.lineHeight + ROW_GAP
        }
    }

    private fun renderRow(
        extra: GuiGraphicsExtractor,
        font: Font,
        panelX: Int,
        panelW: Int,
        y: Int,
        row: HintRow,
        rowColors: RowColors,
    ) {
        val rowRight = panelX + panelW - PAD
        val labelX = rowRight - font.width(row.label)
        extra.text(font, row.label, labelX, y, rowColors.labelColor, true)

        val keyBoxX = labelX - KEY_LABEL_GAP - row.keyBoxW
        extra.fill(keyBoxX, y - 1, keyBoxX + row.keyBoxW, y + font.lineHeight + 1, rowColors.keyBoxBg)
        val keyX = keyBoxX + row.keyBoxW / 2 - font.width(row.keyLabel) / 2
        extra.text(font, row.keyLabel, keyX, y, rowColors.keyColor, true)
    }

    private fun keyBoxWidth(font: Font, keyLabel: String): Int =
        maxOf(KEY_BOX_MIN_W, font.width(keyLabel) + KEY_BOX_PAD_H * 2)

    private data class HintRow(val keyLabel: String, val label: String, val keyBoxW: Int)

    private data class StyledRow(val row: HintRow, val colors: RowColors)

    private data class RowColors(
        val titleColor: Int,
        val labelColor: Int,
        val keyColor: Int,
        val keyBoxBg: Int,
    ) {
        companion object {
            val ACTIVE = RowColors(
                titleColor = TITLE_COLOR_ACTIVE,
                labelColor = LABEL_COLOR_ACTIVE,
                keyColor = KEY_COLOR_ACTIVE,
                keyBoxBg = KEY_BOX_BG_ACTIVE,
            )
            val INACTIVE = RowColors(
                titleColor = TITLE_COLOR_INACTIVE,
                labelColor = LABEL_COLOR_INACTIVE,
                keyColor = KEY_COLOR_INACTIVE,
                keyBoxBg = KEY_BOX_BG_INACTIVE,
            )
        }
    }

    companion object {
        private const val TITLE_KEY = "hud.nmbct-football.hint.title"
        private const val TITLE_KEY_GK = "hud.nmbct-football.hint.title_gk"
        private const val LOOK_AROUND_LABEL_KEY = "hud.nmbct-football.hint.look_around"
        private const val SLIDE_TACKLE_LABEL_KEY = "hud.nmbct-football.hint.slide_tackle"
        private const val BOOST_SPRINT_LABEL_KEY = "hud.nmbct-football.hint.boost_sprint"
        private const val GK_DIVE_INTERRUPT_LABEL_KEY = "hud.nmbct-football.hint.gk_dive_interrupt"
        private const val MARGIN = 8
        private const val PAD = 8
        private const val ROW_GAP = 4
        private const val KEY_BOX_MIN_W = 28
        private const val KEY_BOX_PAD_H = 4
        private const val KEY_LABEL_GAP = 6

        private const val PANEL_BG = 0xCC111122.toInt()

        private const val TITLE_COLOR_ACTIVE = 0xFFFFD700.toInt()
        private const val TITLE_COLOR_INACTIVE = 0xFF6B5A00.toInt()
        private const val LABEL_COLOR_ACTIVE = 0xFFEEEEEE.toInt()
        private const val LABEL_COLOR_INACTIVE = 0xFF666666.toInt()
        private const val KEY_COLOR_ACTIVE = 0xFFFFFFFF.toInt()
        private const val KEY_COLOR_INACTIVE = 0xFF888888.toInt()
        private const val KEY_BOX_BG_ACTIVE = 0x55FFFFFF
        private const val KEY_BOX_BG_INACTIVE = 0x33FFFFFF

        private val OUTFIELD_HINT_ROWS: List<Pair<KeyMapping, String>> = listOf(
            FootballKeyBindings.KICK to "hud.nmbct-football.hint.pass_shoot",
            FootballKeyBindings.DRIBBLE to "hud.nmbct-football.hint.dribble",
            FootballKeyBindings.TRAP to "hud.nmbct-football.hint.trap",
            FootballKeyBindings.CHIP to "hud.nmbct-football.hint.chip",
        )

        private val GK_FREE_HINT_ROWS: List<Pair<KeyMapping, String>> = listOf(
            FootballKeyBindings.KICK to "hud.nmbct-football.hint.gk_dive",
            FootballKeyBindings.TRAP to "hud.nmbct-football.hint.gk_catch",
            FootballKeyBindings.CHIP to "hud.nmbct-football.hint.gk_punch",
        )

        private val GK_HOLDING_HINT_ROWS: List<Pair<KeyMapping, String>> = listOf(
            FootballKeyBindings.KICK to "hud.nmbct-football.hint.gk_throw",
            FootballKeyBindings.TRAP to "hud.nmbct-football.hint.gk_drop",
        )
    }
}
