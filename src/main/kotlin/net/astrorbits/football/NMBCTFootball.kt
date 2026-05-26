package net.astrorbits.football

import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object NMBCTFootball : ModInitializer {
	const val MOD_ID = "nmbct-football"

    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)

	override fun onInitialize() {

	}
}