package net.astrorbits.football

import net.astrorbits.football.block.Blocks
import net.astrorbits.football.config.client.FootballClientConfigHolder
import net.astrorbits.football.config.server.FootballServerConfigHolder
import net.astrorbits.football.input.*
import net.astrorbits.football.item.FootballItemGroups
import net.astrorbits.football.item.FootballItemUseGuards
import net.astrorbits.football.item.GoalNetConnectorSounds
import net.astrorbits.football.item.Items
import net.astrorbits.football.match.*
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.stamina.StaminaState
import net.astrorbits.football.util.GoalNetAnchorLinks
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.resources.Identifier
import net.minecraft.util.ProblemReporter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object NMBCTFootball : ModInitializer {
	const val MOD_ID = "nmbct-football"

    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

	val ROOT_REPORTER: ProblemReporter = ProblemReporter.ScopedCollector(LOGGER)

	inline fun withErrReporter(noinline path: () -> String = { "" }, block: ((errReporter: ProblemReporter) -> Unit)) {
		val errReporter = ROOT_REPORTER.forChild(path)
		block(errReporter)
	}

	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)

	override fun onInitialize() {
		FootballServerConfigHolder.init()
		FootballClientConfigHolder.init()
		MatchConfigHolder.init()

		Items.init()
		Blocks.init()
		FootballItemGroups.init()
		Football.registerSerializers()
		GoalNetEntity.init()
		FootballSounds.init()
		GoalNetConnectorSounds.init()
		FootballParticles.init()

		CommandRegistrationCallback.EVENT.register { dispatcher, context, _ ->
			FootballCommand.register(dispatcher)
			MatchCommand.register(dispatcher, context)
		}

		FootballNetworking.registerPayloadType()
		FootballNetworking.registerServerReceiver()
		FootballNetworking.registerServerTick()
		FootballEntityInteractions.register()
		GoalNetAnchorLinks.registerEvents()
		GoalNetInteractions.register()
		FootballItemUseGuards.register()
		SlideTackleSessions.registerEvents()
		FootballDribbleSessions.registerEvents()
		KickCurveSessions.registerServerTick()
		GoalkeeperDiveSessions.registerEvents()
		SetPieceAreaViolationMonitor.register()
		PostGoalBallResetScheduler.register()
		FootballPlayerCollisionScheduler.register()

		ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
			PlayerRoleState.syncRoleToPlayer(handler.player)
			FootballNetworking.syncConfigToPlayer(handler.player)
			FootballNetworking.syncPlayerJoin(handler.player)
		}
		ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
			val playerId = handler.player.uuid
			StaminaState.removePlayer(playerId)
			FootballPlayerBallContactGrace.removePlayer(playerId)
			FootballKickPushGrace.removePlayer(playerId)
			FootballDribbleSessions.removePlayer(playerId)
			KickCurveSessions.clear(playerId)
		}

		LOGGER.info("NMBCT Football initialized")
	}
}
