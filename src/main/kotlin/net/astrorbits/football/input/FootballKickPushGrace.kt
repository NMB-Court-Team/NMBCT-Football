package net.astrorbits.football.input

import net.astrorbits.football.Football
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 踢球后短暂抑制该球员对同一足球的重复身体冲量，避免推球 + 连点踢球时球在脚前鬼畜。
 * 不阻断碰撞检测。
 */
object FootballKickPushGrace {
    private data class GraceEntry(val footballId: Int, val expiresAtTick: Long)

    private val graceByPlayer = ConcurrentHashMap<UUID, GraceEntry>()

    /** 覆盖 [FootballInputConfig.ACTION_COOLDOWN_TICKS] 及数 tick 的推球/分离结算。 */
    private const val GRACE_TICKS = 10L

    fun record(kicker: ServerPlayer, football: Football, now: Long) {
        graceByPlayer[kicker.uuid] = GraceEntry(football.id, now + GRACE_TICKS)
    }

    fun shouldSuppressPlayerPush(player: ServerPlayer, football: Football, now: Long): Boolean {
        val entry = graceByPlayer[player.uuid] ?: return false
        if (now > entry.expiresAtTick) {
            graceByPlayer.remove(player.uuid, entry)
            return false
        }
        return entry.footballId == football.id
    }

    fun removePlayer(playerId: UUID) {
        graceByPlayer.remove(playerId)
    }

    fun cleanupExpired(now: Long) {
        if (graceByPlayer.isEmpty()) {
            return
        }
        val iterator = graceByPlayer.entries.iterator()
        while (iterator.hasNext()) {
            val (_, entry) = iterator.next()
            if (now > entry.expiresAtTick) {
                iterator.remove()
            }
        }
    }
}
