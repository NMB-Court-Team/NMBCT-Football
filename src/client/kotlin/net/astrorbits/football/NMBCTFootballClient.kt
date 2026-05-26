package net.astrorbits.football

import net.astrorbits.football.client.FootballHudElement
import net.astrorbits.football.client.FootballRenderer
import net.astrorbits.football.match.MatchState
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.renderer.entity.EntityRenderers
import net.minecraft.resources.Identifier

object NMBCTFootballClient : ClientModInitializer {
	override fun onInitializeClient() {
		EntityRenderers.register(Football.ENTITY_TYPE, ::FootballRenderer)

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "match_hud"),
			FootballHudElement()
		)

		ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client ->
			if (MatchState.isRunning && client.level != null && !client.isPaused) {
				MatchState.timerTicks++
			}
		})
	}
}
