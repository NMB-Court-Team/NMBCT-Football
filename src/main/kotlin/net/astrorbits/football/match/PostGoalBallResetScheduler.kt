package net.astrorbits.football.match

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level

/**
 * 进球后延迟将足球复位至开球点（赛场中心）。
 */
object PostGoalBallResetScheduler {
    private data class Pending(
        val dimension: ResourceKey<Level>,
        var ticksRemaining: Int,
    )

    private val pending = ArrayList<Pending>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    fun schedule(level: ServerLevel) {
        val delaySeconds = MatchConfigHolder.current.postGoalBallResetDelaySeconds.coerceAtLeast(0)
        val delayTicks = delaySeconds * 20
        cancel(level.dimension())
        if (delayTicks <= 0) {
            MatchState.resetFootball(level)
            return
        }
        pending.add(Pending(level.dimension(), delayTicks))
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
            MatchState.resetFootball(level)
        }
    }
}
