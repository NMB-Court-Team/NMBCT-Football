package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.GoalLineOutClient
import net.astrorbits.football.client.match.MatchHudTeams
import net.astrorbits.football.match.GoalLineOutType
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class GoalLineOutHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!GoalLineOutClient.isActive) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val typeKey = when (GoalLineOutClient.outType) {
            GoalLineOutType.CORNER_KICK -> "hud.nmbct-football.out.corner_kick"
            GoalLineOutType.GOAL_KICK -> "hud.nmbct-football.out.goal_kick"
            GoalLineOutType.THROW_IN -> "hud.nmbct-football.out.throw_in"
        }
        val typeColor = when (GoalLineOutClient.outType) {
            GoalLineOutType.CORNER_KICK -> 0xFFFF9800.toInt()
            GoalLineOutType.GOAL_KICK -> 0xFF4CAF50.toInt()
            GoalLineOutType.THROW_IN -> 0xFF4488FF.toInt()
        }
        val restartName = MatchHudTeams.name(GoalLineOutClient.restartTeam)

        val touchLine = if (GoalLineOutClient.lastTouchPlayerName.isNotBlank()) {
            val team = GoalLineOutClient.lastTouchTeam
            val touchColor = if (team != null) MatchEventBanner.teamColor(team) else 0xFFCCCCCC.toInt()
            Component.translatable(
                "hud.nmbct-football.out.kicked_by",
                GoalLineOutClient.lastTouchPlayerName,
            ).string to touchColor
        } else {
            Component.translatable("hud.nmbct-football.out.kicked_by_unknown").string to 0xFFAAAAAA.toInt()
        }

        val restartLine = Component.translatable("hud.nmbct-football.out.restart", restartName).string

        MatchEventBanner.renderOut(
            extra = extra,
            font = client.font,
            screenW = client.window.guiScaledWidth,
            screenH = client.window.guiScaledHeight,
            elapsedMs = GoalLineOutClient.elapsedMs,
            durationMs = 4000L,
            typeText = Component.translatable(typeKey).string,
            typeColor = typeColor,
            touchLine = touchLine.first,
            touchColor = touchLine.second,
            restartLine = restartLine,
            restartColor = MatchEventBanner.teamColor(GoalLineOutClient.restartTeam),
        )
    }
}
