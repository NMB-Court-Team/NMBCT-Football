package net.astrorbits.football.config.server

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.config.ConfigPersistence
import net.fabricmc.loader.api.FabricLoader

object FootballServerConfigHolder {
    @Volatile
    var current: FootballServerConfig = FootballServerConfig.DEFAULT
        private set

    private val configPath = FabricLoader.getInstance().configDir.resolve("nmbct-football-server.json")

    fun init() {
        current = ConfigPersistence.load(configPath, FootballServerConfig.CODEC, FootballServerConfig.DEFAULT)
    }

    fun apply(config: FootballServerConfig) {
        current = config
        ConfigPersistence.save(configPath, FootballServerConfig.CODEC, config)
        NMBCTFootball.LOGGER.info("Applied server football config")
    }
}
