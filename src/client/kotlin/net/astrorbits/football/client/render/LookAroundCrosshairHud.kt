package net.astrorbits.football.client.render

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.client.key.LookAroundClient
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

object LookAroundCrosshairHud {
    private val SPRITE: Identifier = Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "hud/crosshair_look_around")

    private const val WIDTH = 15
    private const val HEIGHT = 7

    fun register() {
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR) { original ->
            HudElement { extra, delta ->
                if (LookAroundClient.active) {
                    renderLookAroundCrosshair(extra)
                } else {
                    original.extractRenderState(extra, delta)
                }
            }
        }
    }

    private fun renderLookAroundCrosshair(extra: GuiGraphicsExtractor) {
        val client = Minecraft.getInstance()
        val screenW = client.window.guiScaledWidth
        val screenH = client.window.guiScaledHeight
        val x = (screenW - WIDTH) / 2
        val y = (screenH - HEIGHT) / 2
        extra.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITE, x, y, WIDTH, HEIGHT)
    }
}
