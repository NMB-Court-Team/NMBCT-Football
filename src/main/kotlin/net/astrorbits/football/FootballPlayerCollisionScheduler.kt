package net.astrorbits.football

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * 将足球与玩家的身体碰撞推迟到本 server tick 末尾执行。
 *
 * 足球 [Football.serverTick] 的 entityId 若小于玩家，会在玩家位移写入之前跑碰撞，
 * 导致 `x-xOld` 与 intent 混用、推球力度因 entityId 不一致。END_SERVER_TICK 时所有实体已 tick 完毕。
 */
object FootballPlayerCollisionScheduler {
    private data class Pending(
        val dimension: ResourceKey<Level>,
        val footballId: Int,
        val beforeMove: Vec3,
        val afterMove: Vec3,
        val intendedMotion: Vec3,
    )

    private val pending = ArrayList<Pending>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::flush)
    }

    fun schedule(
        football: Football,
        beforeMove: Vec3,
        afterMove: Vec3,
        intendedMotion: Vec3,
    ) {
        val level = football.level() as? ServerLevel ?: return
        pending.add(
            Pending(
                dimension = level.dimension(),
                footballId = football.id,
                beforeMove = beforeMove,
                afterMove = afterMove,
                intendedMotion = intendedMotion,
            ),
        )
    }

    private fun flush(server: MinecraftServer) {
        if (pending.isEmpty()) {
            return
        }
        val batch = pending.toList()
        pending.clear()
        for (entry in batch) {
            val level = server.getLevel(entry.dimension) ?: continue
            val football = level.getEntity(entry.footballId) as? Football ?: continue
            if (!football.isAlive) {
                continue
            }
            football.runDeferredPlayerCollisions(entry.beforeMove, entry.afterMove, entry.intendedMotion)
        }
    }
}
