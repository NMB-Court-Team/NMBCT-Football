package net.astrorbits.football.client.render

import net.astrorbits.football.client.GoalkeeperHoldStealProtectionClient
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/** 守门员持球抢球保护期间，在十字准心下方显示提示。 */
class GoalkeeperHoldStealProtectionHudElement : HudElement {
    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        val client = Minecraft.getInstance()
        if (client.screen != null || client.isPaused) {
            return
        }
        val player = client.player ?: return
        if (!GoalkeeperHoldStealProtectionClient.shouldShowHud(player)) {
            return
        }

        val font = client.font
        val label = Component.translatable(LABEL_KEY).string
        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        val cx = width / 2f
        val baseY = height / 2f + CROSSHAIR_BELOW_OFFSET

        val pose = extra.pose()
        pose.pushMatrix()
        pose.translate(cx, baseY)
        pose.scale(TEXT_SCALE, TEXT_SCALE)
        val textW = font.width(label)
        extra.text(font, label, (-textW / 2f).toInt(), 0, LABEL_COLOR, true)
        pose.popMatrix()
    }

    companion object {
        private const val LABEL_KEY = "hud.nmbct-football.gk_hold_steal_protection"
        private const val LABEL_COLOR = 0xFF42A5F5.toInt()
        private const val TEXT_SCALE = 1.0f
        /** 准心中心略下方（准心约 15px 高）；与区域违规警告错开。 */
        private const val CROSSHAIR_BELOW_OFFSET = 12f
    }
}
