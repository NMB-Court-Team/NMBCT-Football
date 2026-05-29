package net.astrorbits.football.match

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.config.ConfigPersistence
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component

object MatchConfigHolder {
    @Volatile
    var current: MatchConfig = MatchConfig.DEFAULT
        private set

    private val configPath = FabricLoader.getInstance().configDir.resolve("nmbct-football-match.json")

    fun init() {
        current = ConfigPersistence.load(configPath, MatchConfig.CODEC, MatchConfig.DEFAULT)
        MatchState.teamAName = Component.literal(current.teamAName)
        MatchState.teamBName = Component.literal(current.teamBName)
    }

    fun apply(config: MatchConfig) {
        current = config
        ConfigPersistence.save(configPath, MatchConfig.CODEC, config)
        MatchState.teamAName = Component.literal(config.teamAName)
        MatchState.teamBName = Component.literal(config.teamBName)
        NMBCTFootball.LOGGER.info("Applied match config")
    }
}
