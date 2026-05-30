package net.astrorbits.football.client.config

import net.astrorbits.football.match.MatchConfig
import net.astrorbits.football.network.MatchConfigApplyC2SPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class MatchConfigScreen(
    private val parent: Screen?,
    private val initial: MatchConfig,
) : Screen(TITLE) {

    private var teamAName = initial.teamAName
    private var teamBName = initial.teamBName
    private var halfTimeMinutes = initial.halfTimeMinutes
    private var stoppageTimeMaxMinutes = initial.stoppageTimeMaxMinutes
    private var extraTimeHalfMinutes = initial.extraTimeHalfMinutes

    private var updatingEditBox = false

    private var currentTab = 0

    private lateinit var stoppageCheck: Checkbox
    private lateinit var extraCheck: Checkbox
    private lateinit var penaltyCheck: Checkbox

    override fun init() {
        buildTabBar()
        buildCurrentTab()
        buildBottomButtons()
    }

    private fun buildTabBar() {
        val tabW = 100
        val tabH = 20
        val totalTabW = tabW * 3 + 4
        val startX = (width - totalTabW) / 2
        val tabY = 28

        for (i in 0 until 3) {
            val tabComponent = TAB_NAMES[i]
            val btn = Button.builder(tabComponent) { switchTab(i) }
                .bounds(startX + i * (tabW + 2), tabY, tabW, tabH)
                .build()
            btn.active = i != currentTab
            addRenderableWidget(btn)
        }
    }

    private fun buildCurrentTab() {
        val labelX = width / 2 - 155
        val widgetX = width / 2 + 5
        val labelW = 150
        val widgetW = 150
        val editH = 20
        val rowH = 26
        val startY = 58

        when (currentTab) {
            0 -> {
                var y = startY
                addRenderableWidget(StringWidget(labelX, y, labelW, editH, TEAM_A_NAME, font))
                val teamAEdit = EditBox(font, widgetX, y, widgetW, editH, TEAM_A_NAME)
                teamAEdit.setValue(teamAName)
                teamAEdit.setResponder { teamAName = it }
                addRenderableWidget(teamAEdit)

                y += rowH
                addRenderableWidget(StringWidget(labelX, y, labelW, editH, TEAM_B_NAME, font))
                val teamBEdit = EditBox(font, widgetX, y, widgetW, editH, TEAM_B_NAME)
                teamBEdit.setValue(teamBName)
                teamBEdit.setResponder { teamBName = it }
                addRenderableWidget(teamBEdit)
            }
            1 -> {
                var y = startY
                addRenderableWidget(StringWidget(labelX, y, labelW, editH, HALF_TIME, font))
                addRenderableWidget(newDigitEditBox(widgetX, y, 52, editH, halfTimeMinutes.toString(), HALF_TIME) {
                    halfTimeMinutes = it
                })

                y += rowH
                stoppageCheck = Checkbox.builder(STOPPAGE_TIME, font)
                    .pos(widgetX, y)
                    .selected(initial.enableStoppageTime)
                    .maxWidth(widgetW)
                    .build()
                addRenderableWidget(stoppageCheck)

                y += rowH
                addRenderableWidget(StringWidget(labelX, y, labelW, editH, STOPPAGE_TIME_MAX, font))
                addRenderableWidget(newDigitEditBox(widgetX, y, 52, editH, stoppageTimeMaxMinutes.toString(), STOPPAGE_TIME_MAX) {
                    stoppageTimeMaxMinutes = it
                })
            }
            2 -> {
                var y = startY
                extraCheck = Checkbox.builder(EXTRA_TIME, font)
                    .pos(widgetX, y)
                    .selected(initial.enableExtraTime)
                    .maxWidth(widgetW)
                    .build()
                addRenderableWidget(extraCheck)

                y += rowH
                addRenderableWidget(StringWidget(labelX, y, labelW, editH, EXTRA_TIME_HALF, font))
                addRenderableWidget(newDigitEditBox(widgetX, y, 52, editH, extraTimeHalfMinutes.toString(), EXTRA_TIME_HALF) {
                    extraTimeHalfMinutes = it
                })

                y += rowH
                penaltyCheck = Checkbox.builder(PENALTY_SHOOTOUT, font)
                    .pos(widgetX, y)
                    .selected(initial.enablePenaltyShootout)
                    .maxWidth(widgetW)
                    .build()
                addRenderableWidget(penaltyCheck)
            }
        }
    }

    private fun buildBottomButtons() {
        val btnY = height - 50
        addRenderableWidget(
            Button.builder(SAVE) { onSave() }
                .bounds(width / 2 - 102, btnY, 100, 20).build()
        )
        addRenderableWidget(
            Button.builder(CANCEL) { onClose() }
                .bounds(width / 2 + 2, btnY, 100, 20).build()
        )
    }

    private fun switchTab(index: Int) {
        if (index == currentTab) return
        currentTab = index
        clearWidgets()
        buildTabBar()
        buildCurrentTab()
        buildBottomButtons()
    }

    private fun newDigitEditBox(
        x: Int, y: Int, w: Int, h: Int, initialText: String, message: Component,
        onValidChange: (Int) -> Unit,
    ): EditBox {
        val edit = EditBox(font, x, y, w, h, message)
        edit.setValue(initialText)
        edit.setResponder { text ->
            if (updatingEditBox) return@setResponder
            val filtered = text.filter { it.isDigit() }
            if (filtered != text) {
                updatingEditBox = true
                edit.setValue(filtered)
                updatingEditBox = false
            }
            val parsed = filtered.toIntOrNull()
            if (parsed != null) onValidChange(parsed)
        }
        return edit
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

    private fun onSave() {
        val config = MatchConfig(
            teamAName = teamAName,
            teamBName = teamBName,
            halfTimeMinutes = halfTimeMinutes.coerceAtLeast(1),
            enableStoppageTime = if (::stoppageCheck.isInitialized) stoppageCheck.selected() else initial.enableStoppageTime,
            stoppageTimeMaxMinutes = stoppageTimeMaxMinutes.coerceAtLeast(0),
            enableExtraTime = if (::extraCheck.isInitialized) extraCheck.selected() else initial.enableExtraTime,
            extraTimeHalfMinutes = extraTimeHalfMinutes.coerceAtLeast(1),
            enablePenaltyShootout = if (::penaltyCheck.isInitialized) penaltyCheck.selected() else initial.enablePenaltyShootout,
            goalA = initial.goalA,
            goalB = initial.goalB,
            teamASpawn = initial.teamASpawn,
            teamBSpawn = initial.teamBSpawn,
        )
        if (ClientPlayNetworking.canSend(MatchConfigApplyC2SPayload.TYPE)) {
            ClientPlayNetworking.send(MatchConfigApplyC2SPayload(config))
        }
        onClose()
    }

    companion object {
        private val TITLE = Component.translatable("screen.nmbct-football.match.title")
        private val TEAM_A_NAME = Component.translatable("screen.nmbct-football.match.team_a_name")
        private val TEAM_B_NAME = Component.translatable("screen.nmbct-football.match.team_b_name")
        private val HALF_TIME = Component.translatable("screen.nmbct-football.match.half_time_minutes")
        private val STOPPAGE_TIME = Component.translatable("screen.nmbct-football.match.enable_stoppage_time")
        private val STOPPAGE_TIME_MAX = Component.translatable("screen.nmbct-football.match.stoppage_time_max")
        private val EXTRA_TIME = Component.translatable("screen.nmbct-football.match.enable_extra_time")
        private val EXTRA_TIME_HALF = Component.translatable("screen.nmbct-football.match.extra_time_half")
        private val PENALTY_SHOOTOUT = Component.translatable("screen.nmbct-football.match.enable_penalty_shootout")
        private val SAVE = Component.translatable("screen.nmbct-football.match.save")
        private val CANCEL = Component.translatable("screen.nmbct-football.match.cancel")
        private val TAB_TEAMS = Component.translatable("screen.nmbct-football.match.tab.teams")
        private val TAB_TIMING = Component.translatable("screen.nmbct-football.match.tab.timing")
        private val TAB_EXTRA = Component.translatable("screen.nmbct-football.match.tab.extra")
        private val TAB_NAMES = arrayOf(TAB_TEAMS, TAB_TIMING, TAB_EXTRA)
    }
}
