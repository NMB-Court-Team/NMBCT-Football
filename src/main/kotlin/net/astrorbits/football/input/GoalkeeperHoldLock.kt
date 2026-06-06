package net.astrorbits.football.input

import net.astrorbits.football.network.FootballNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 守门员持球后的释放锁定：接球/鱼跃摘球后短时间内禁止手抛、开球与放下。
 */
object GoalkeeperHoldLock {
    private val releaseLockUntilTick = ConcurrentHashMap<UUID, Long>()

    fun beginLock(
        player: ServerPlayer,
        now: Long,
        lockTicks: Int = GoalkeeperInputConfig.GK_HOLD_RELEASE_LOCK_TICKS,
    ) {
        if (lockTicks <= 0) {
            return
        }
        val until = now + lockTicks
        releaseLockUntilTick[player.uuid] = until
        syncToPlayer(player, now)
    }

    fun onHoldEnded(player: ServerPlayer) {
        if (releaseLockUntilTick.remove(player.uuid) != null) {
            FootballNetworking.sendHoldReleaseLock(player, 0)
        }
    }

    fun isReleaseBlocked(player: ServerPlayer, now: Long): Boolean {
        val until = releaseLockUntilTick[player.uuid] ?: return false
        if (now >= until) {
            releaseLockUntilTick.remove(player.uuid)
            FootballNetworking.sendHoldReleaseLock(player, 0)
            return false
        }
        return true
    }

    fun remainingTicks(player: ServerPlayer, now: Long): Int {
        val until = releaseLockUntilTick[player.uuid] ?: return 0
        return (until - now).toInt().coerceAtLeast(0)
    }

    private fun syncToPlayer(player: ServerPlayer, now: Long) {
        FootballNetworking.sendHoldReleaseLock(player, remainingTicks(player, now))
    }

    /** 比赛重置时清除所有持球释放锁定并通知客户端。 */
    fun clearAll(server: MinecraftServer) {
        if (releaseLockUntilTick.isEmpty()) {
            return
        }
        val locked = releaseLockUntilTick.keys.toList()
        releaseLockUntilTick.clear()
        for (uuid in locked) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            FootballNetworking.sendHoldReleaseLock(player, 0)
        }
    }
}
