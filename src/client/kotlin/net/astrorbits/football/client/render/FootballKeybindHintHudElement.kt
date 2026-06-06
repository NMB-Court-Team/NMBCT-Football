package net.astrorbits.football.client.render



import net.astrorbits.football.client.FootballOperabilityClient
import net.astrorbits.football.client.GoalkeeperStateClient
import net.astrorbits.football.client.key.FootballInputHandler
import net.astrorbits.football.client.key.FootballKeyBindings
import net.astrorbits.football.client.util.FootballHudVisibility
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

        val rows = if (FootballOperabilityClient.canShowFootballHints(player)) {

            buildFootballHintRows(font, player, level)

        } else {

            listOf(

                StyledRow(

                    buildHintRow(font, FootballKeyBindings.LOOK_AROUND, LOOK_AROUND_LABEL_KEY),

                    RowColors.ACTIVE,

                ),

            )

        }



        val titleColor = if (rows.any { it.colors == RowColors.ACTIVE }) {

            RowColors.ACTIVE.titleColor

        } else {

            RowColors.INACTIVE.titleColor

        }

        renderPanel(

            extra = extra,

            font = font,

            screenW = client.window.guiScaledWidth,

            title = Component.translatable(TITLE_KEY).string,

            titleColor = titleColor,

            rows = rows,

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



    private fun buildFootballHintRows(font: Font, player: LocalPlayer, level: Level): List<StyledRow> {

        val holdingBall = GoalkeeperStateClient.isHoldingBall

        val hintRows = buildFootballActionRows(holdingBall).toMutableList()

        if (FootballInputHandler.isAnyChargeActive()) {

            hintRows += FootballKeyBindings.INTERRUPT_CHARGE to INTERRUPT_CHARGE_LABEL_KEY

        }

        hintRows += FootballKeyBindings.LOOK_AROUND to LOOK_AROUND_LABEL_KEY



        return hintRows.map { (key, labelKey) ->

            val row = buildHintRow(font, key, labelKey)

            val colors = if (FootballOperabilityClient.canUseFootballHint(player, level, key)) {

                RowColors.ACTIVE

            } else {

                RowColors.INACTIVE

            }

            StyledRow(row, colors)

        }

    }



    private fun buildFootballActionRows(holdingBall: Boolean): List<Pair<KeyMapping, String>> {
        if (holdingBall) {
            return listOf(
                FootballKeyBindings.GK_DIVE to GK_THROW_LABEL_KEY,
                FootballKeyBindings.GK_CATCH to GK_DROP_LABEL_KEY,
                FootballKeyBindings.BOOST_SPRINT to BOOST_SPRINT_LABEL_KEY,
            )
        }

        return listOf(
            FootballKeyBindings.KICK to PASS_SHOOT_STRIKE_LABEL_KEY,
            FootballKeyBindings.DRIBBLE to DRIBBLE_LABEL_KEY,
            FootballKeyBindings.TRAP to TRAP_LABEL_KEY,
            FootballKeyBindings.CHIP to CHIP_LABEL_KEY,
            FootballKeyBindings.GK_DIVE to GK_DIVE_LABEL_KEY,
            FootballKeyBindings.GK_CATCH to GK_CATCH_LABEL_KEY,
            FootballKeyBindings.SLIDE_TACKLE to SLIDE_TACKLE_LABEL_KEY,
            FootballKeyBindings.BOOST_SPRINT to BOOST_SPRINT_LABEL_KEY,
        )
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

        private const val LOOK_AROUND_LABEL_KEY = "hud.nmbct-football.hint.look_around"

        private const val PASS_SHOOT_STRIKE_LABEL_KEY = "hud.nmbct-football.hint.pass_shoot_strike"

        private const val DRIBBLE_LABEL_KEY = "hud.nmbct-football.hint.dribble"

        private const val SLIDE_TACKLE_LABEL_KEY = "hud.nmbct-football.hint.slide_tackle"

        private const val BOOST_SPRINT_LABEL_KEY = "hud.nmbct-football.hint.boost_sprint"

        private const val TRAP_LABEL_KEY = "hud.nmbct-football.hint.trap"

        private const val CHIP_LABEL_KEY = "hud.nmbct-football.hint.chip"

        private const val GK_DIVE_LABEL_KEY = "hud.nmbct-football.hint.gk_dive"

        private const val GK_THROW_LABEL_KEY = "hud.nmbct-football.hint.gk_throw"

        private const val GK_CATCH_LABEL_KEY = "hud.nmbct-football.hint.gk_catch"

        private const val GK_DROP_LABEL_KEY = "hud.nmbct-football.hint.gk_drop"

        private const val INTERRUPT_CHARGE_LABEL_KEY = "hud.nmbct-football.hint.interrupt_charge"

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

    }

}


