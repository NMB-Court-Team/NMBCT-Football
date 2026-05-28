package net.astrorbits.football.client

import net.astrorbits.football.Football
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.KeyMapping
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level

class FootballKeybindHintHudElement : HudElement {
    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        val client = Minecraft.getInstance()
        if (client.screen != null || client.isPaused) {
            return
        }
        val level = client.level ?: return
        val player = client.player ?: return
        if (!player.mainHandItem.isEmpty) {
            return
        }

        val actionsActive = canOperateFootball(player, level)
        val titleColor = if (actionsActive) TITLE_COLOR_ACTIVE else TITLE_COLOR_INACTIVE
        val labelColor = if (actionsActive) LABEL_COLOR_ACTIVE else LABEL_COLOR_INACTIVE
        val keyColor = if (actionsActive) KEY_COLOR_ACTIVE else KEY_COLOR_INACTIVE
        val keyBoxBg = if (actionsActive) KEY_BOX_BG_ACTIVE else KEY_BOX_BG_INACTIVE

        val font = client.font
        val screenW = client.window.guiScaledWidth
        val rows = buildHintRows().map { (key, labelKey) ->
            val keyLabel = key.translatedKeyMessage.string
            HintRow(
                keyLabel = keyLabel,
                label = Component.translatable(labelKey).string,
                keyBoxW = keyBoxWidth(font, keyLabel),
            )
        }

        val titleKey = if (GoalkeeperStateClient.isGoalkeeper) {
            TITLE_KEY_GK
        } else {
            TITLE_KEY
        }
        val title = Component.translatable(titleKey).string
        val titleW = font.width(title)
        val rowContentW = rows.maxOf { font.width(it.label) + it.keyBoxW + KEY_LABEL_GAP }
        val panelW = PAD * 2 + maxOf(titleW, rowContentW)
        val panelH = PAD + font.lineHeight + ROW_GAP + rows.size * (font.lineHeight + ROW_GAP) + PAD
        val panelX = screenW - MARGIN - panelW
        val panelY = MARGIN

        extra.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG)
        extra.text(font, title, panelX + panelW - PAD - font.width(title), panelY + PAD, titleColor, true)

        var y = panelY + PAD + font.lineHeight + ROW_GAP
        for (row in rows) {
            val rowRight = panelX + panelW - PAD
            val labelX = rowRight - font.width(row.label)
            extra.text(font, row.label, labelX, y, labelColor, true)

            val keyBoxX = labelX - KEY_LABEL_GAP - row.keyBoxW
            extra.fill(keyBoxX, y - 1, keyBoxX + row.keyBoxW, y + font.lineHeight + 1, keyBoxBg)
            val keyX = keyBoxX + row.keyBoxW / 2 - font.width(row.keyLabel) / 2
            extra.text(font, row.keyLabel, keyX, y, keyColor, true)
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

    /**
     * 当前是否满足进行足球操作的基本条件（与 [FootballInputConfig.PLAYER_KICK_RANGE] / 守门员距离一致）。
     */
    private fun canOperateFootball(player: LocalPlayer, level: Level): Boolean {
        if (GoalkeeperStateClient.isGoalkeeper) {
            if (GoalkeeperStateClient.isHoldingBall) {
                return !GoalkeeperStateClient.isHoldReleaseLocked()
            }
            val range = GoalkeeperInputConfig.GK_CATCH_RANGE + GoalkeeperInputConfig.GK_CROUCH_RANGE_BONUS
            return nearestOperableFootball(player, level, range) != null
        }

        return nearestOperableFootball(player, level, FootballInputConfig.PLAYER_KICK_RANGE) != null
    }

    private fun nearestOperableFootball(player: LocalPlayer, level: Level, range: Double): Football? {
        return level.getEntitiesOfClass(
            Football::class.java,
            player.boundingBox.inflate(range),
        )
            .filter { !it.isHeld() && player.distanceToSqr(it) <= range * range }
            .minByOrNull { it.distanceToSqr(player) }
    }

    private fun keyBoxWidth(font: Font, keyLabel: String): Int =
        maxOf(KEY_BOX_MIN_W, font.width(keyLabel) + KEY_BOX_PAD_H * 2)

    private data class HintRow(val keyLabel: String, val label: String, val keyBoxW: Int)

    companion object {
        private const val TITLE_KEY = "hud.nmbct-football.hint.title"
        private const val TITLE_KEY_GK = "hud.nmbct-football.hint.title_gk"
        private const val MARGIN = 8
        private const val PAD = 8
        private const val ROW_GAP = 4
        private const val KEY_BOX_MIN_W = 28
        /** 按键方框左右内边距（各一侧）。 */
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
