package net.astrorbits.football.client

import net.astrorbits.football.Football
import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.GoalNetEntity
import net.astrorbits.football.client.render.GoalNetConnectorPreviewClient
import net.astrorbits.football.client.render.GoalNetRenderer
import net.astrorbits.football.client.render.GoalNetStateClient
import net.astrorbits.football.client.match.MatchHudDebugClient
import net.astrorbits.football.client.match.MatchStateClient
import net.astrorbits.football.client.config.FootballClientConfigKeyHandler
import net.astrorbits.football.client.config.FootballConfigNetworking
import net.astrorbits.football.client.config.MatchConfigNetworking
import net.astrorbits.football.client.config.MatchFieldConfigNetworking
import net.astrorbits.football.client.match.GoalScoredClientNetworking
import net.astrorbits.football.client.match.FreeKickAwardClientNetworking
import net.astrorbits.football.client.match.InvalidGoalClientNetworking
import net.astrorbits.football.client.match.MatchStartClientNetworking
import net.astrorbits.football.client.render.FreeKickAwardHudElement
import net.astrorbits.football.client.render.GoalLineOutHudElement
import net.astrorbits.football.client.render.GoalScoredHudElement
import net.astrorbits.football.client.render.InvalidGoalHudElement
import net.astrorbits.football.client.render.HalfKickoffHudElement
import net.astrorbits.football.client.render.KickoffLockHudElement
import net.astrorbits.football.client.render.MatchPauseHudElement
import net.astrorbits.football.client.render.MatchPauseOverlayHudElement
import net.astrorbits.football.client.render.PreMatchPrepHudElement
import net.astrorbits.football.client.render.MatchResultHudElement
import net.astrorbits.football.client.render.PenaltyKickHudElement
import net.astrorbits.football.client.render.MatchStartHudElement
import net.astrorbits.football.client.item.GoalNetConnectorItemTooltipClient
import net.astrorbits.football.client.key.FootballInputHandler
import net.astrorbits.football.client.key.FootballKeyBindings
import net.astrorbits.football.client.key.LookAroundClient
import net.astrorbits.football.client.render.LookAroundCrosshairHud
import net.astrorbits.football.client.render.FootballHudElement
import net.astrorbits.football.client.render.FootballKeybindHintHudElement
import net.astrorbits.football.client.render.FootballRenderer
import net.astrorbits.football.client.render.GoalkeeperHoldLockHudElement
import net.astrorbits.football.client.render.GoalkeeperHoldStealProtectionHudElement
import net.astrorbits.football.client.GoalkeeperHoldStealProtectionClient
import net.astrorbits.football.client.render.KickChargeHudElement
import net.astrorbits.football.client.render.BoostSprintHudElement
import net.astrorbits.football.client.render.DribbleBallOffscreenHudElement
import net.astrorbits.football.client.render.StaminaHudElement
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.renderer.entity.EntityRenderers
import net.minecraft.resources.Identifier

object NMBCTFootballClient : ClientModInitializer {
	override fun onInitializeClient() {
		EntityRenderers.register(Football.ENTITY_TYPE, ::FootballRenderer)
		EntityRenderers.register(GoalNetEntity.ENTITY_TYPE, ::GoalNetRenderer)

		GoalNetStateClient.register()
		GoalNetConnectorPreviewClient.register()
		GoalNetConnectorItemTooltipClient.register()
		FootballConfigNetworking.register()
		StaminaClientNetworking.register()
		MatchConfigNetworking.register()
		MatchFieldConfigNetworking.register()
		MatchStartClientNetworking.register()
		GoalScoredClientNetworking.register()
		InvalidGoalClientNetworking.register()
		FreeKickAwardClientNetworking.register()

		FootballKeyBindings.init()
		FootballClientConfigKeyHandler.register()
		FootballDevClientCommands.register()

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
		HudElementRegistry.attachElementAfter(
			net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CROSSHAIR,
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "gk_hold_steal_protection_hud"),
			GoalkeeperHoldStealProtectionHudElement(),
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
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "invalid_goal_hud"),
			InvalidGoalHudElement()
		)

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "goal_line_out_hud"),
			GoalLineOutHudElement()
		)

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "free_kick_award_hud"),
			FreeKickAwardHudElement()
		)

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "match_pause_hud"),
			MatchPauseHudElement()
		)

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "match_pause_overlay_hud"),
			MatchPauseOverlayHudElement()
		)

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "half_kickoff_hud"),
			HalfKickoffHudElement()
		)

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "pre_match_prep_hud"),
			PreMatchPrepHudElement()
		)

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "match_result_hud"),
			MatchResultHudElement()
		)

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "penalty_kick_hud"),
			PenaltyKickHudElement()
		)

		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "stamina_hud"),
			StaminaHudElement()
		)
		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "boost_sprint_hud"),
			BoostSprintHudElement()
		)
		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "dribble_ball_offscreen_hud"),
			DribbleBallOffscreenHudElement()
		)

		ClientTickEvents.END_CLIENT_TICK.register { client ->
			StaminaClient.tick(client)
			MatchHudDebugClient.tick()
		}

		GoalkeeperStateClient.register()
		GoalkeeperHoldStealProtectionClient.register()
		GoalkeeperHoldPoseClient.register()
		SlideTackleStateClient.register()
		WhistleSoundsClient.register()
		FootballClientAttackInteractions.register()
		FootballInputHandler.registerTickEvent()
		LookAroundClient.register()
		LookAroundCrosshairHud.register()
		MatchStateClient.registerTickEvent()
	}
}