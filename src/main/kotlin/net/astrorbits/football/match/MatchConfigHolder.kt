package net.astrorbits.football.match

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.config.ConfigPersistence
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Files
import java.nio.file.Path

object MatchConfigHolder {
    const val CONFIG_FILE_NAME = "nmbct-football-match.json"

    @Volatile
    var current: MatchConfig = MatchConfig.DEFAULT
        private set

    private val globalConfigPath: Path =
        FabricLoader.getInstance().configDir.resolve(CONFIG_FILE_NAME)

    @Volatile
    private var activeConfigPath: Path = globalConfigPath

    fun init() {
        ServerLifecycleEvents.SERVER_STARTED.register(::onServerStarted)
        loadFromPath(globalConfigPath, createIfMissing = true)
    }

    private fun onServerStarted(server: MinecraftServer) {
        val worldConfigPath = server.getWorldPath(LevelResource.ROOT).resolve(CONFIG_FILE_NAME)
        if (Files.exists(worldConfigPath)) {
            activeConfigPath = worldConfigPath
            loadFromPath(worldConfigPath, createIfMissing = false)
            NMBCTFootball.LOGGER.info("Loaded match config from world save: {}", worldConfigPath)
        } else {
            activeConfigPath = globalConfigPath
            loadFromPath(globalConfigPath, createIfMissing = true)
            NMBCTFootball.LOGGER.info("Loaded match config from global config: {}", globalConfigPath)
        }
    }

    private fun loadFromPath(path: Path, createIfMissing: Boolean) {
        val loaded = if (createIfMissing) {
            ConfigPersistence.load(path, MatchConfig.CODEC, MatchConfig.DEFAULT)
        } else {
            ConfigPersistence.loadIfExists(path, MatchConfig.CODEC, MatchConfig.DEFAULT)
        }
        val migrated = MatchConfig.migrateFieldConfigIfNeeded(loaded)
        if (migrated !== loaded) {
            ConfigPersistence.save(path, MatchConfig.CODEC, migrated)
        }
        applyInMemory(migrated)
    }

    private fun applyInMemory(config: MatchConfig) {
        current = config
        MatchState.teamAName = Component.literal(config.teamAName)
        MatchState.teamBName = Component.literal(config.teamBName)
    }

    fun apply(config: MatchConfig) {
        applyInMemory(config)
        ConfigPersistence.save(activeConfigPath, MatchConfig.CODEC, config)
        NMBCTFootball.LOGGER.info("Applied match config to {}", activeConfigPath)
    }

    /** 客户端接收服务端推送时使用，仅更新内存不写文件。 */
    fun syncFromServer(config: MatchConfig) {
        applyInMemory(config)
    }
}
