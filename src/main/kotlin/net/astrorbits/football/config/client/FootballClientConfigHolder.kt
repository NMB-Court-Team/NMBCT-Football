package net.astrorbits.football.config.client

import net.astrorbits.football.config.ConfigPersistence
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.FabricLoader

object FootballClientConfigHolder {
    @Volatile
    var current: FootballClientConfig = FootballClientConfig.DEFAULT
        private set

    private val configPath = FabricLoader.getInstance().configDir.resolve("nmbct-football-client.json")

    fun init() {
        if (FabricLoader.getInstance().environmentType != EnvType.CLIENT) {
            return
        }
        current = ConfigPersistence.load(configPath, FootballClientConfig.CODEC, FootballClientConfig.DEFAULT)
    }

    fun apply(config: FootballClientConfig) {
        current = config
        if (FabricLoader.getInstance().environmentType == EnvType.CLIENT) {
            ConfigPersistence.save(configPath, FootballClientConfig.CODEC, config)
        }
    }
}
