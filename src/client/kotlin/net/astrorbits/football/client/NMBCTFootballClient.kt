package net.astrorbits.football.client

import net.astrorbits.football.Football
import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.client.match.MatchStateClient
import net.astrorbits.football.client.config.FootballConfigNetworking
import net.astrorbits.football.client.config.MatchConfigNetworking
import net.astrorbits.football.client.config.MatchFieldConfigNetworking
import net.astrorbits.football.client.match.GoalScoredClientNetworking
import net.astrorbits.football.client.match.MatchStartClientNetworking
import net.astrorbits.football.client.render.GoalScoredHudElement
import net.astrorbits.football.client.render.HalfKickoffHudElement
import net.astrorbits.football.client.render.KickoffLockHudElement
import net.astrorbits.football.client.render.MatchResultHudElement
import net.astrorbits.football.client.render.MatchStartHudElement
import net.astrorbits.football.client.key.FootballInputHandler
import net.astrorbits.football.client.key.FootballKeyBindings
import net.astrorbits.football.client.key.LookAroundClient
import net.astrorbits.football.client.render.LookAroundCrosshairHud
import net.astrorbits.football.client.render.FootballHudElement
import net.astrorbits.football.client.render.FootballKeybindHintHudElement
import net.astrorbits.football.client.render.FootballRenderer
import net.astrorbits.football.client.render.GoalkeeperHoldLockHudElement
import net.astrorbits.football.client.render.KickChargeHudElement
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.renderer.entity.EntityRenderers
import net.minecraft.resources.Identifier

object NMBCTFootballClient : ClientModInitializer {
	override fun onInitializeClient() {
		EntityRenderers.register(Football.ENTITY_TYPE, ::FootballRenderer)

		FootballConfigNetworking.register()
		MatchConfigNetworking.register()
		MatchFieldConfigNetworking.register()
		MatchStartClientNetworking.register()
		GoalScoredClientNetworking.register()

		FootballKeyBindings.init()

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "match_hud"),
			FootballHudElement()
		)
		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "kick_charge_hud"),
			KickChargeHudElement()
		)
		HudElementRegistry.attachElementAfter(
			net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.SUBTITLES,
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "keybind_hint_hud"),
			FootballKeybindHintHudElement()
		)

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "gk_hold_lock_hud"),
			GoalkeeperHoldLockHudElement()
		)

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "match_start_hud"),
			MatchStartHudElement()
		)

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "kickoff_lock_hud"),
			KickoffLockHudElement()
		)

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "goal_scored_hud"),
			GoalScoredHudElement()
		)

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "half_kickoff_hud"),
			HalfKickoffHudElement()
		)

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "match_result_hud"),
			MatchResultHudElement()
		)

		GoalkeeperStateClient.register()
		GoalkeeperHoldPoseClient.register()
		FootballClientAttackInteractions.register()
		FootballInputHandler.registerTickEvent()
		LookAroundClient.register()
		LookAroundCrosshairHud.register()
		MatchStateClient.registerTickEvent()
	}
}