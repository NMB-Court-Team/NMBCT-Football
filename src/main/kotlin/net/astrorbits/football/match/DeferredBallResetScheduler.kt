package net.astrorbits.football.match

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.SectionPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * 球员传送后，等待目标区块加载完毕再清除残留足球并生成新球。
 * 避免仅在已加载区块清球、传送后新区块仍带有旧足球实体的问题。
 */
object DeferredBallResetScheduler {
    private data class Pending(
        val dimension: ResourceKey<Level>,
        var ticksRemaining: Int,
        val resetPos: Vec3?,
        val onComplete: ((ServerLevel) -> Unit)?,
    )

    private val pending = ArrayList<Pending>()

    /** 传送后等待区块加载的默认时长（2 秒）。 */
    const val LOAD_WAIT_TICKS = 40

    private const val CHUNK_PRELOAD_RADIUS = 1

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    fun schedule(
        level: ServerLevel,
        resetPos: Vec3?,
        delayTicks: Int = LOAD_WAIT_TICKS,
        onComplete: ((ServerLevel) -> Unit)? = null,
    ) {
        cancel(level.dimension())
        pending.add(Pending(level.dimension(), delayTicks, resetPos, onComplete))
    }

    fun cancel(dimension: ResourceKey<Level>) {
        pending.removeIf { it.dimension == dimension }
    }

    private fun tick(server: MinecraftServer) {
        if (pending.isEmpty()) return
        val iter = pending.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            entry.ticksRemaining--
            if (entry.ticksRemaining > 0) continue
            iter.remove()
            val level = server.getLevel(entry.dimension) ?: continue
            preloadMatchFieldChunks(level, server)
            MatchState.resetFootball(level, entry.resetPos)
            entry.onComplete?.invoke(level)
        }
    }

    private fun preloadMatchFieldChunks(level: ServerLevel, server: MinecraftServer) {
        val config = MatchConfigHolder.current
        val positions = mutableListOf<Vec3>()
        val kickOff = config.kickOff
        positions.add(Vec3(kickOff.x, kickOff.y, kickOff.z))
        for (spawn in listOf(config.teamASpawn, config.teamBSpawn)) {
            positions.add(Vec3(spawn.gk.x, spawn.gk.y, spawn.gk.z))
            for (playerSpawn in spawn.players) {
                positions.add(Vec3(playerSpawn.x, playerSpawn.y, playerSpawn.z))
            }
        }
        for (player in server.playerList.players) {
            positions.add(player.position())
        }

        val loaded = mutableSetOf<Pair<Int, Int>>()
        for (pos in positions) {
            val chunkX = SectionPos.blockToSectionCoord(pos.x.toInt())
            val chunkZ = SectionPos.blockToSectionCoord(pos.z.toInt())
            for (dx in -CHUNK_PRELOAD_RADIUS..CHUNK_PRELOAD_RADIUS) {
                for (dz in -CHUNK_PRELOAD_RADIUS..CHUNK_PRELOAD_RADIUS) {
                    val cx = chunkX + dx
                    val cz = chunkZ + dz
                    if (loaded.add(cx to cz)) {
                        level.getChunk(cx, cz)
                    }
                }
            }
        }
    }
}
