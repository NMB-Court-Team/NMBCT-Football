package net.astrorbits.football.client.render

import net.astrorbits.football.client.SetPieceClient
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.match.GoalKickPhase
import net.astrorbits.football.match.SetPieceKind
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

/** 球门球流程操作提示（与 [KickoffLockHudElement] 等待对方开球同一位置）。 */
class GoalKickHintHudElement : HudElement {
    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (SetPieceClient.kind != SetPieceKind.GOAL_KICK) return

        val client = Minecraft.getInstance()
        if (client.screen != null || client.isPaused) return
        val player = client.player ?: return
        if (player.isSpectator) return

        val phase = SetPieceClient.goalKickPhase ?: return
        val restartTeam = SetPieceClient.restartTeam ?: return

        val labelKey = when (phase) {
            GoalKickPhase.WAITING_PICKUP -> {
                if (MatchStartClient.playerTeam != restartTeam) return
                PICKUP_HINT_KEY
            }
            GoalKickPhase.PLACING -> {
                if (player.uuid != SetPieceClient.goalKickPickerUuid) return
                PLACE_HINT_KEY
            }
            else -> return
        }

        val font = client.font
        val text = Component.translatable(labelKey).string
        val cx = client.window.guiScaledWidth / 2
        val y = client.window.guiScaledHeight - HINT_Y_OFFSET
        drawCenter(extra, font, text, cx, y, HINT_COLOR)
    }

    private fun drawCenter(
        extra: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        text: String,
        cx: Int,
        y: Int,
        color: Int,
    ) {
        val bold = Component.literal(text).withStyle(Style.EMPTY.withBold(true))
        extra.text(font, bold.visualOrderText, cx - font.width(text) / 2, y, color, true)
    }

    companion object {
        private const val PICKUP_HINT_KEY = "hud.nmbct-football.goal_kick.hint.pickup"
        private const val PLACE_HINT_KEY = "hud.nmbct-football.goal_kick.hint.place"
        /** 与 [KickoffLockHudElement] 中「请等待对方开球」一致。 */
        private const val HINT_Y_OFFSET = 128
        private const val HINT_COLOR = 0xFFFFAA00.toInt()
    }
}
