package net.astrorbits.football.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.KeyMapping
import net.minecraft.network.chat.Component

class FootballKeybindHintHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (client.screen != null || client.isPaused) return
        if (!player.mainHandItem.isEmpty) return

        val font = client.font
        val screenW = client.window.guiScaledWidth
        val rows = HINT_ROWS.map { (key, labelKey) ->
            HintRow(key.getTranslatedKeyMessage().string, Component.translatable(labelKey).string)
        }

        val title = Component.translatable(TITLE_KEY).string
        val titleW = font.width(title)
        val rowContentW = rows.maxOf { font.width(it.label) + KEY_BOX_W + KEY_LABEL_GAP }
        val panelW = PAD * 2 + maxOf(titleW, rowContentW)
        val panelH = PAD + font.lineHeight + ROW_GAP + rows.size * (font.lineHeight + ROW_GAP) + PAD
        val panelX = screenW - MARGIN - panelW
        val panelY = MARGIN

        extra.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG)
        extra.text(font, title, panelX + panelW - PAD - font.width(title), panelY + PAD, TITLE_COLOR, true)

        var y = panelY + PAD + font.lineHeight + ROW_GAP
        for (row in rows) {
            val rowRight = panelX + panelW - PAD
            val labelX = rowRight - font.width(row.label)
            extra.text(font, row.label, labelX, y, LABEL_COLOR, true)

            val keyBoxX = labelX - KEY_LABEL_GAP - KEY_BOX_W
            extra.fill(keyBoxX, y - 1, keyBoxX + KEY_BOX_W, y + font.lineHeight + 1, KEY_BOX_BG)
            val keyX = keyBoxX + KEY_BOX_W / 2 - font.width(row.keyLabel) / 2
            extra.text(font, row.keyLabel, keyX, y, KEY_COLOR, true)
            y += font.lineHeight + ROW_GAP
        }
    }

    private data class HintRow(val keyLabel: String, val label: String)

    companion object {
        private const val TITLE_KEY = "hud.nmbct-football.hint.title"
        private const val MARGIN = 8
        private const val PAD = 8
        private const val ROW_GAP = 4
        private const val KEY_BOX_W = 28
        private const val KEY_LABEL_GAP = 6

        private val PANEL_BG = 0xCC111122.toInt()
        private val KEY_BOX_BG = 0x55FFFFFF
        private val TITLE_COLOR = 0xFFFFD700.toInt()
        private val KEY_COLOR = 0xFFFFFFFF.toInt()
        private val LABEL_COLOR = 0xFFCCCCCC.toInt()

        private val HINT_ROWS: List<Pair<KeyMapping, String>> = listOf(
            FootballKeyBindings.KICK to "hud.nmbct-football.hint.pass_shoot",
            FootballKeyBindings.DRIBBLE to "hud.nmbct-football.hint.dribble",
            FootballKeyBindings.TRAP to "hud.nmbct-football.hint.trap",
            FootballKeyBindings.CHIP to "hud.nmbct-football.hint.chip"
        )
    }
}
