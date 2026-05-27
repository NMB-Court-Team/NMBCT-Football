package net.astrorbits.football

import net.astrorbits.football.item.Items
import net.astrorbits.football.match.MatchCommand
import net.astrorbits.football.network.FootballNetworking
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.resources.Identifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object NMBCTFootball : ModInitializer {
	const val MOD_ID = "nmbct-football"

    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)

	override fun onInitialize() {
		Items.init()
		Football.init()

		CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
			FootballCommand.register(dispatcher)
			MatchCommand.register(dispatcher)
		}

		FootballNetworking.registerPayloadType()
		FootballNetworking.registerServerReceiver()

		LOGGER.info("NMBCT Football initialized")
	}
}
