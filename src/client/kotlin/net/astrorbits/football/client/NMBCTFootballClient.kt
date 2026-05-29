package net.astrorbits.football.client

import net.astrorbits.football.Football
import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.client.match.MatchStateClient
import net.astrorbits.football.config.FootballConfigNetworking
import net.astrorbits.football.config.MatchConfigNetworking
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.renderer.entity.EntityRenderers
import net.minecraft.resources.Identifier

object NMBCTFootballClient : ClientModInitializer {
	override fun onInitializeClient() {
		EntityRenderers.register(Football.ENTITY_TYPE, ::FootballRenderer)

		FootballConfigNetworking.register()
		MatchConfigNetworking.register()

		FootballKeyBindings.init()

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "match_hud"),
			FootballHudElement()
		)
		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "kick_charge_hud"),
			KickChargeHudElement()
		)
		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "keybind_hint_hud"),
			FootballKeybindHintHudElement()
		)

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "gk_hold_lock_hud"),
			GoalkeeperHoldLockHudElement()
		)

		GoalkeeperStateClient.register()
		GoalkeeperHoldPoseClient.register()
		FootballClientAttackInteractions.register()
		FootballInputHandler.registerTickEvent()
		MatchStateClient.registerTickEvent()
	}
}