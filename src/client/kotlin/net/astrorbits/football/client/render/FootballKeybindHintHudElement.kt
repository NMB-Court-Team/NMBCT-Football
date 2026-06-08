package net.astrorbits.football.client.render

import net.astrorbits.football.client.util.FootballHudVisibility
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component

class FootballKeybindHintHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        val client = Minecraft.getInstance()
        if (!canRenderHud(client)) return

        val player = client.player ?: return
        val level = client.level ?: return

        val content = FootballHudHintResolver.resolve(
            FootballHudHintResolver.Position.TOP_RIGHT,
            player,
            level,
            delta,
        ) as? FootballHudHintResolver.Content.Keybinds ?: return

        val panel = content.panel
        val font = client.font
        val rows = panel.rows.map { row ->
            val hintRow = HintRow(
                keyLabel = row.key.translatedKeyMessage.string,
                label = Component.translatable(row.labelKey).string,
                keyBoxW = keyBoxWidth(font, row.key.translatedKeyMessage.string),
            )
            StyledRow(hintRow, if (row.active) RowColors.ACTIVE else RowColors.INACTIVE)
        }

        val titleColor = if (panel.titleActive) RowColors.ACTIVE.titleColor else RowColors.INACTIVE.titleColor
        renderPanel(
            extra = extra,
            font = font,
            screenW = client.window.guiScaledWidth,
            title = Component.translatable(panel.titleKey).string,
            titleColor = titleColor,
            rows = rows,
        )
    }

    private fun canRenderHud(client: Minecraft): Boolean {
        if (client.isPaused || FootballHudVisibility.isDebugOverlayOpen(client)) return false
        if (client.player == null || client.level == null) return false
        val screen = client.screen
        return screen == null || screen is ChatScreen
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
        val panelX = screenW - FootballHudHintResolver.Layout.TOP_RIGHT_MARGIN - panelW
        val panelY = FootballHudHintResolver.Layout.TOP_RIGHT_MARGIN

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
    }
}
