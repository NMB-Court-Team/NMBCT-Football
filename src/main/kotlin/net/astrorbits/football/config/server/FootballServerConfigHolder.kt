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
        val normalized = normalize(config)
        current = normalized
        ConfigPersistence.save(configPath, FootballServerConfig.CODEC, normalized)
        NMBCTFootball.LOGGER.info("Applied server football config")
    }

    /** 客户端接收服务端广播的配置（不写本地磁盘）。 */
    fun syncFromServer(config: FootballServerConfig) {
        current = normalize(config)
    }

    private fun normalize(config: FootballServerConfig): FootballServerConfig {
        val sm = config.staminaMechanism
        val optimizedTiers = StaminaMechanismSettings.optimizeSpeedTiers(sm.speedTiers)
        if (optimizedTiers == sm.speedTiers) {
            return config
        }
        return config.copy(staminaMechanism = sm.copy(speedTiers = optimizedTiers))
    }
}
