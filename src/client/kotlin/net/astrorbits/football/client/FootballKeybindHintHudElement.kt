package net.astrorbits.football.client

import net.astrorbits.football.Football
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.KeyMapping
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level

class FootballKeybindHintHudElement : HudElement {
    /** 滞后状态：在临界距离附近保持上一帧显示/隐藏，避免闪烁。 */
    private var hintVisible = false

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        val client = Minecraft.getInstance()
        if (client.screen != null || client.isPaused) {
            hintVisible = false
            return
        }
        val level = client.level ?: return
        val player = client.player ?: return
        if (!player.mainHandItem.isEmpty) {
            hintVisible = false
            return
        }
        if (!updateHintVisibility(player, level)) {
            return
        }

        val font = client.font
        val screenW = client.window.guiScaledWidth
        val rows = buildHintRows().map { (key, labelKey) ->
            HintRow(key.translatedKeyMessage.string, Component.translatable(labelKey).string)
        }

        val titleKey = if (GoalkeeperStateClient.isGoalkeeper) {
            TITLE_KEY_GK
        } else {
            TITLE_KEY
        }
        val title = Component.translatable(titleKey).string
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

    private fun buildHintRows(): List<Pair<KeyMapping, String>> {
        if (!GoalkeeperStateClient.isGoalkeeper) {
            return OUTFIELD_HINT_ROWS
        }
        return if (GoalkeeperStateClient.isHoldingBall) {
            GK_HOLDING_HINT_ROWS
        } else {
            GK_FREE_HINT_ROWS
        }
    }

    private fun updateHintVisibility(player: LocalPlayer, level: Level): Boolean {
        val range = if (GoalkeeperStateClient.isGoalkeeper) {
            GoalkeeperInputConfig.GK_CATCH_RANGE + GoalkeeperInputConfig.GK_CROUCH_RANGE_BONUS
        } else {
            FootballInputConfig.PLAYER_KICK_RANGE
        }
        val hideRange = range + FootballInputConfig.HINT_HIDE_EXTRA_RANGE
        val nearestDistSq = level.getEntitiesOfClass(
            Football::class.java,
            player.boundingBox.inflate(hideRange),
        ).minOfOrNull { it.distanceToSqr(player) }

        if (GoalkeeperStateClient.isGoalkeeper && GoalkeeperStateClient.isHoldingBall) {
            hintVisible = true
            return true
        }

        if (nearestDistSq == null) {
            hintVisible = false
            return false
        }

        val showRangeSq = range * range
        val hideRangeSq = hideRange * hideRange
        hintVisible = when {
            nearestDistSq <= showRangeSq -> true
            nearestDistSq > hideRangeSq -> false
            else -> hintVisible
        }
        return hintVisible
    }

    private data class HintRow(val keyLabel: String, val label: String)

    companion object {
        private const val TITLE_KEY = "hud.nmbct-football.hint.title"
        private const val TITLE_KEY_GK = "hud.nmbct-football.hint.title_gk"
        private const val MARGIN = 8
        private const val PAD = 8
        private const val ROW_GAP = 4
        private const val KEY_BOX_W = 28
        private const val KEY_LABEL_GAP = 6

        private const val PANEL_BG = 0xCC111122.toInt()
        private const val KEY_BOX_BG = 0x55FFFFFF
        private const val TITLE_COLOR = 0xFFFFD700.toInt()
        private const val KEY_COLOR = 0xFFFFFFFF.toInt()
        private const val LABEL_COLOR = 0xFFCCCCCC.toInt()

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
