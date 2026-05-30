package net.astrorbits.football

import net.astrorbits.football.block.Blocks
import net.astrorbits.football.config.client.FootballClientConfigHolder
import net.astrorbits.football.config.server.FootballServerConfigHolder
import net.astrorbits.football.input.FootballDribbleSessions
import net.astrorbits.football.input.GoalkeeperDiveSessions
import net.astrorbits.football.item.FootballItemGroups
import net.astrorbits.football.item.Items
import net.astrorbits.football.item.FootballItemUseGuards
import net.astrorbits.football.match.MatchCommand
import net.astrorbits.football.match.MatchConfigHolder
import net.astrorbits.football.match.PlayerRoleState
import net.astrorbits.football.network.FootballNetworking
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.resources.Identifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object NMBCTFootball : ModInitializer {
	const val MOD_ID = "nmbct-football"

    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)

	override fun onInitialize() {
		FootballServerConfigHolder.init()
		FootballClientConfigHolder.init()
		MatchConfigHolder.init()

		Items.init()
		Blocks.init()
		FootballItemGroups.init()
		Football.init()
		FootballSounds.init()
		FootballParticles.init()

		CommandRegistrationCallback.EVENT.register { dispatcher, context, _ ->
			FootballCommand.register(dispatcher)
			MatchCommand.register(dispatcher, context)
		}

		FootballNetworking.registerPayloadType()
		FootballNetworking.registerServerReceiver()
		FootballEntityInteractions.register()
		FootballItemUseGuards.register()
		FootballDribbleSessions.registerEvents()
		GoalkeeperDiveSessions.registerEvents()

		ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
			PlayerRoleState.syncRoleToPlayer(handler.player)
		}

		LOGGER.info("NMBCT Football initialized")
	}
}
