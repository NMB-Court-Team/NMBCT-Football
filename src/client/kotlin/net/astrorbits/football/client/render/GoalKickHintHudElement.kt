package net.astrorbits.football.client.render

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor

/** 球门球操作提示已并入 [KickoffLockHudElement] / [SetPieceRoleHintResolver]。 */
class GoalKickHintHudElement : HudElement {
    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) = Unit
}
