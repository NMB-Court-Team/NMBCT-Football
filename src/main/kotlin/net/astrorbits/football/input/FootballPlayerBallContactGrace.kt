package net.astrorbits.football.input

import net.astrorbits.football.Football
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 身体触球后短暂抑制对该球的重复冲量，避免球速不足时卡在玩家碰撞箱内鬼畜。
 * 接触当 tick 仍正常结算；从下一 tick 起生效。不阻断碰撞检测。
 */
object FootballPlayerBallContactGrace {
    private data class GraceEntry(
        val footballId: Int,
        val recordedAtTick: Long,
        val expiresAtTick: Long,
    )

    private val graceByPlayer = ConcurrentHashMap<UUID, GraceEntry>()

    fun record(player: ServerPlayer, football: Football, now: Long, sliding: Boolean) {
        val graceTicks = if (sliding) {
            FootballInputConfig.SLIDE_BALL_CONTACT_GRACE_TICKS
        } else {
            FootballInputConfig.PLAYER_BALL_CONTACT_GRACE_TICKS
        }.coerceAtLeast(0)
        if (graceTicks <= 0) {
            graceByPlayer.remove(player.uuid)
            return
        }
        graceByPlayer[player.uuid] = GraceEntry(
            footballId = football.id,
            recordedAtTick = now,
            expiresAtTick = now + graceTicks,
        )
    }

    fun shouldSuppressBodyImpulse(player: ServerPlayer, football: Football, now: Long): Boolean {
        val entry = graceByPlayer[player.uuid] ?: return false
        if (now > entry.expiresAtTick) {
            graceByPlayer.remove(player.uuid, entry)
            return false
        }
        if (now <= entry.recordedAtTick) {
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
