package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.GoalLineOutClient
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.match.GoalLineOutType
import net.astrorbits.football.match.TeamSide
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

class GoalLineOutHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!GoalLineOutClient.isActive) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val font = client.font
        val w = client.window.guiScaledWidth
        val h = client.window.guiScaledHeight
        val scale = 3.0f
        val cx = (w / 2f / scale).toInt()
        val baseY = (h / 2f / scale).toInt() - 18

        val remaining = 4000L - GoalLineOutClient.elapsedMs
        val alpha = ((remaining.coerceIn(0L, 1000L) / 1000f) * 255).toInt()
        val fade = { color: Int -> (alpha shl 24) or (color and 0xFFFFFF) }

        val pose = extra.pose()
        pose.pushMatrix()
        pose.scale(scale, scale)

        // 类型文字：角球 / 球门球
        val typeKey = when (GoalLineOutClient.outType) {
            GoalLineOutType.CORNER_KICK -> "hud.nmbct-football.out.corner_kick"
            GoalLineOutType.GOAL_KICK -> "hud.nmbct-football.out.goal_kick"
            GoalLineOutType.THROW_IN -> "hud.nmbct-football.out.throw_in"
        }
        val typeText = Component.translatable(typeKey).string
        val typeColor = when (GoalLineOutClient.outType) {
            GoalLineOutType.CORNER_KICK -> 0xFFFF9800.toInt()
            GoalLineOutType.GOAL_KICK -> 0xFF4CAF50.toInt()
            GoalLineOutType.THROW_IN -> 0xFF4488FF.toInt()
        }
        drawBold(extra, font, typeText, cx - font.width(typeText) / 2, baseY, fade(typeColor))

        // 发球方队名
        val teamName = if (GoalLineOutClient.restartTeam == TeamSide.A) MatchStartClient.teamAName else MatchStartClient.teamBName
        val teamColor = if (GoalLineOutClient.restartTeam == TeamSide.A) 0xFFFF5555.toInt() else 0xFF55FFFF.toInt()
        val prefix = Component.translatable("hud.nmbct-football.out.kick_by").string
        val fullText = "$prefix $teamName"
        drawBold(extra, font, fullText, cx - font.width(fullText) / 2, baseY + 18, fade(teamColor))

        pose.popMatrix()
    }

    private fun drawBold(extra: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, text: String, x: Int, y: Int, color: Int) {
        val bold = Component.literal(text).withStyle(Style.EMPTY.withBold(true))
        extra.text(font, bold.visualOrderText, x, y, color, true)
    }
}
