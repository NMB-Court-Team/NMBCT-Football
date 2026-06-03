package net.astrorbits.football.input

import net.astrorbits.football.Football
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.FootballParticles
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.PlayerRoleState
import net.astrorbits.football.network.FootballActionC2SPayload
import net.astrorbits.football.util.FootballKickUtil
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class DribbleSession(
    val playerId: UUID,
    val footballId: Int,
    var lastHeartbeatTick: Long,
    var nextSoundTick: Long,
    /** 观察四周期间带球：以进入观察时的 yaw 为基准，null 表示按当前视角。 */
    var dribbleBaseYaw: Float? = null,
)

data class DribbleCollisionGrace(
    val footballId: Int,
    val expiresAtTick: Long,
)

object FootballDribbleSessions {
    private val sessions = ConcurrentHashMap<UUID, DribbleSession>()
    private val collisionGrace = ConcurrentHashMap<UUID, DribbleCollisionGrace>()

    fun registerEvents() {
        // 在实体 tick 之前更新球速，避免 END tick 设置速度导致整 tick 滞后（滑铲高速下尤其明显）
        ServerTickEvents.START_SERVER_TICK.register(::tick)
    }

    /**
     * 更新运球 session 心跳；按 [FootballInputConfig.DRIBBLE_SOUND_INTERVAL_TICKS] 尝试播放触球音效。
     */
    fun beginOrRefresh(player: ServerPlayer, football: Football, now: Long, payload: FootballActionC2SPayload) {
        if (MatchState.isKickoffInteractionLocked(player)) {
            return
        }
        if (PlayerRoleState.isGoalkeeper(player)) {
            return
        }

        val lookAroundActive = payload.flags and FootballInputConfig.FLAG_LOOK_AROUND != 0
        collisionGrace.remove(player.uuid)
        val existing = sessions[player.uuid]
        if (existing == null || existing.footballId != football.id) {
            val session = DribbleSession(
                playerId = player.uuid,
                footballId = football.id,
                lastHeartbeatTick = now,
                nextSoundTick = now,
                dribbleBaseYaw = if (lookAroundActive) payload.lookYaw else null,
            )
            sessions[player.uuid] = session
            if (FootballInputConfig.DRIBBLE_TOUCH_FORCE > 0.0) {
                FootballKickUtil.applyDribbleTouch(player, football, session.dribbleBaseYaw)
            }
            tryPlayDribbleSound(session, player, football, now)
            return
        }

        existing.lastHeartbeatTick = now
        if (lookAroundActive) {
            if (existing.dribbleBaseYaw == null) {
                existing.dribbleBaseYaw = payload.lookYaw
            }
        } else {
            existing.dribbleBaseYaw = null
        }
        tryPlayDribbleSound(existing, player, football, now)
    }

    fun end(player: ServerPlayer) {
        val removed = sessions.remove(player.uuid) ?: return
        val now = player.level().gameTime
        recordCollisionGrace(player.uuid, removed.footballId, now)
    }

    fun end(playerId: UUID) {
        sessions.remove(playerId)
        collisionGrace.remove(playerId)
    }

    fun tick(server: MinecraftServer) {
        if (sessions.isEmpty()) {
            return
        }

        val now = server.overworld().gameTime
        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            val (playerId, session) = iterator.next()
            val player = server.playerList.getPlayer(playerId)
            if (player == null) {
                iterator.remove()
                continue
            }

            if (MatchState.isKickoffInteractionLocked(player)) {
                recordCollisionGrace(playerId, session.footballId, now)
                iterator.remove()
                continue
            }

            if (now - session.lastHeartbeatTick > FootballInputConfig.DRIBBLE_SESSION_TIMEOUT_TICKS) {
                recordCollisionGrace(playerId, session.footballId, now)
                iterator.remove()
                continue
            }

            val football = player.level().getEntity(session.footballId)
            if (football !is Football || !football.isAlive) {
                iterator.remove()
                continue
            }

            if (!FootballDribbleAssist.apply(player, football, session.dribbleBaseYaw)) {
                recordCollisionGrace(playerId, session.footballId, now)
                iterator.remove()
                continue
            }

            tryPlayDribbleSound(session, player, football, now)
        }
        cleanupExpiredCollisionGrace(now)
    }

    fun shouldIgnoreCollision(player: ServerPlayer, football: Football, now: Long): Boolean {
        val session = sessions[player.uuid]
        if (session != null && session.footballId == football.id) {
            return true
        }
        val grace = collisionGrace[player.uuid] ?: return false
        if (now > grace.expiresAtTick) {
            collisionGrace.remove(player.uuid, grace)
            return false
        }
        return grace.footballId == football.id
    }

    private fun recordCollisionGrace(playerId: UUID, footballId: Int, now: Long) {
        val graceTicks = FootballInputConfig.DRIBBLE_COLLISION_GRACE_TICKS.coerceAtLeast(0)
        if (graceTicks <= 0) {
            collisionGrace.remove(playerId)
            return
        }
        collisionGrace[playerId] = DribbleCollisionGrace(
            footballId = footballId,
            expiresAtTick = now + graceTicks,
        )
    }

    private fun cleanupExpiredCollisionGrace(now: Long) {
        if (collisionGrace.isEmpty()) return
        val iterator = collisionGrace.entries.iterator()
        while (iterator.hasNext()) {
            val (_, entry) = iterator.next()
            if (now > entry.expiresAtTick) {
                iterator.remove()
            }
        }
    }

    private fun tryPlayDribbleSound(session: DribbleSession, player: ServerPlayer, football: Football, now: Long) {
        if (now < session.nextSoundTick) {
            return
        }
        FootballSounds.playDribble(player)
        FootballParticles.playDribble(player, football)
        session.nextSoundTick = now + resolveDribbleSoundIntervalTicks(player.random)
    }

    private fun resolveDribbleSoundIntervalTicks(random: RandomSource): Long {
        val base = FootballInputConfig.DRIBBLE_SOUND_INTERVAL_TICKS
        val jitter = FootballInputConfig.DRIBBLE_SOUND_INTERVAL_JITTER_TICKS.coerceAtLeast(0)
        if (jitter == 0) return base.toLong().coerceAtLeast(1L)
        val offset = random.nextInt(jitter * 2 + 1) - jitter
        return (base + offset).toLong().coerceAtLeast(1L)
    }
}
