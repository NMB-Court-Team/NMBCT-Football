package net.astrorbits.football.match

import net.astrorbits.football.Football
import net.minecraft.server.MinecraftServer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.*

/** 比赛暂停时全场足球的速度清零、仅受重力下坠，并记录/恢复各球的可操作状态。 */
object MatchPauseFootballState {
    private val snapshots = mutableMapOf<Int, Snapshot>()

    private data class Snapshot(
        val immovableTargetPlayers: Set<UUID>,
        val isImmovable: Boolean,
    )

    private val ALL_FOOTBALLS_AABB = AABB(Vec3(-3.0E7, -3.0E7, -3.0E7), Vec3(3.0E7, 3.0E7, 3.0E7))

    fun onPause(server: MinecraftServer) {
        snapshots.clear()
        val lockedPlayers = server.playerList.players.map { it.uuid }.toSet()
        for (level in server.allLevels) {
            for (football in level.getEntitiesOfClass(Football::class.java, ALL_FOOTBALLS_AABB)) {
                snapshots[football.id] = Snapshot(
                    football.immovableTargetPlayers,
                    football.isImmovable,
                )
                football.enterMatchPause(lockedPlayers)
            }
        }
    }

    fun onResume(server: MinecraftServer) {
        for (level in server.allLevels) {
            for (football in level.getEntitiesOfClass(Football::class.java, ALL_FOOTBALLS_AABB)) {
                val snapshot = snapshots.remove(football.id)
                if (snapshot != null) {
                    football.restoreFromMatchPause(snapshot.immovableTargetPlayers, snapshot.isImmovable)
                } else {
                    football.restoreFromMatchPause(emptySet(), isImmovable = false)
                }
            }
        }
        snapshots.clear()
    }
}
