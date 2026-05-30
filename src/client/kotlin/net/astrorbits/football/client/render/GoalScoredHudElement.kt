package net.astrorbits.football.client.render

import net.astrorbits.football.client.match.GoalScoredClient
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.match.TeamSide
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

class GoalScoredHudElement : HudElement {

    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!GoalScoredClient.isActive) return
        val client = Minecraft.getInstance()
        if (client.isPaused) return

        val font = client.font
        val w = client.window.guiScaledWidth
        val h = client.window.guiScaledHeight
        val scale = 3.0f
        val cx = (w / 2f / scale).toInt()
        val baseY = (h / 2f / scale).toInt() - 36

        val remaining = 4000L - GoalScoredClient.elapsedMs
        val alpha = ((remaining.coerceIn(0L, 1000L) / 1000f) * 255).toInt()
        val fade = { color: Int -> (alpha shl 24) or (color and 0xFFFFFF) }

        val pose = extra.pose()
        pose.pushMatrix()
        pose.scale(scale, scale)

        val scoring = GoalScoredClient.scoringTeam
        val myTeam = MatchStartClient.playerTeam
        val scoringColor = if (scoring == TeamSide.A) 0xFFFF5555.toInt() else 0xFF55FFFF.toInt()
        val otherColor = if (scoring == TeamSide.A) 0xFF55FFFF.toInt() else 0xFFFF5555.toInt()

        // 乌龙球时顶部显示进球者所在队伍（失误方），非乌龙显示得分方
        val topTeam = if (GoalScoredClient.ownGoal) GoalScoredClient.scorerTeam else scoring
        val topColor = if (topTeam == TeamSide.A) 0xFFFF5555.toInt() else 0xFF55FFFF.toInt()

        val topName = if (topTeam == TeamSide.A) MatchStartClient.teamAName else MatchStartClient.teamBName
        val scoringName = if (scoring == TeamSide.A) MatchStartClient.teamAName else MatchStartClient.teamBName
        val otherName = if (scoring != TeamSide.A) MatchStartClient.teamAName else MatchStartClient.teamBName
        val scoringScore = if (scoring == TeamSide.A) GoalScoredClient.teamAScore else GoalScoredClient.teamBScore
        val otherScore = if (scoring != TeamSide.A) GoalScoredClient.teamAScore else GoalScoredClient.teamBScore

        var y = baseY

        // team name (乌龙球显示失误方，正常进球显示得分方)
        drawBold(extra, font, topName, cx - font.width(topName) / 2, y, fade(topColor))
        y += 18

        // scorer name
        val scorerText = GoalScoredClient.scorerName
        drawBold(extra, font, scorerText, cx - font.width(scorerText) / 2, y, fade(0xFFFFFFFF.toInt()))
        y += 18

        // goal / own goal
        val myScoring = scoring == myTeam
        val goalKey: String
        val goalColor: Int
        if (GoalScoredClient.ownGoal) {
            goalKey = "hud.nmbct-football.goal.own_goal"
            goalColor = if (myScoring) 0xFF55FF55.toInt() else 0xFF888888.toInt()
        } else {
            goalKey = "hud.nmbct-football.goal.goal"
            goalColor = if (myScoring) 0xFF55FF55.toInt() else 0xFFAA55FF.toInt()
        }
        val goalText = Component.translatable(goalKey).string
        drawBold(extra, font, goalText, cx - font.width(goalText) / 2, y, fade(goalColor))
        y += 28

        // score line: 得分方  比分 - 比分  另一方
        val scoreStr = "$scoringScore"
        val otherStr = "$otherScore"
        val dash = " - "
        val gap = font.width("  ")
        val totalW = font.width(scoringName) + gap + font.width(scoreStr) + font.width(dash) + font.width(otherStr) + gap + font.width(otherName)
        var sx = cx - totalW / 2
        drawBold(extra, font, scoringName, sx, y, fade(scoringColor)); sx += font.width(scoringName) + gap
        drawBold(extra, font, scoreStr, sx, y, fade(0xFFFFFFFF.toInt())); sx += font.width(scoreStr)
        drawBold(extra, font, dash, sx, y, fade(0xFF888888.toInt())); sx += font.width(dash)
        drawBold(extra, font, otherStr, sx, y, fade(0xFFFFFFFF.toInt())); sx += font.width(otherStr) + gap
        drawBold(extra, font, otherName, sx, y, fade(otherColor))

        pose.popMatrix()
    }

    private fun drawBold(extra: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, text: String, x: Int, y: Int, color: Int) {
        val bold = Component.literal(text).withStyle(Style.EMPTY.withBold(true))
        extra.text(font, bold.visualOrderText, x, y, color, true)
    }
}
