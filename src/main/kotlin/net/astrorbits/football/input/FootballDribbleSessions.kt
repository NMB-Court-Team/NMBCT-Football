package net.astrorbits.football.input

import net.astrorbits.football.Football
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.FootballParticles
import net.astrorbits.football.match.PlayerRoleState
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
)

object FootballDribbleSessions {
    private val sessions = ConcurrentHashMap<UUID, DribbleSession>()

    fun registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    /**
     * 更新运球 session 心跳；按 [FootballInputConfig.DRIBBLE_SOUND_INTERVAL_TICKS] 尝试播放触球音效。
     */
    fun beginOrRefresh(player: ServerPlayer, football: Football, now: Long) {
        if (PlayerRoleState.isGoalkeeper(player)) {
            return
        }

        val existing = sessions[player.uuid]
        if (existing == null || existing.footballId != football.id) {
            val session = DribbleSession(
                playerId = player.uuid,
                footballId = football.id,
                lastHeartbeatTick = now,
                nextSoundTick = now,
            )
            sessions[player.uuid] = session
            if (FootballInputConfig.DRIBBLE_TOUCH_FORCE > 0.0) {
                FootballKickUtil.applyDribbleTouch(player, football)
            }
            tryPlayDribbleSound(session, player, football, now)
            return
        }

        existing.lastHeartbeatTick = now
        tryPlayDribbleSound(existing, player, football, now)
    }

    fun end(player: ServerPlayer) {
        sessions.remove(player.uuid)
    }

    fun end(playerId: UUID) {
        sessions.remove(playerId)
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

            if (now - session.lastHeartbeatTick > FootballInputConfig.DRIBBLE_SESSION_TIMEOUT_TICKS) {
                iterator.remove()
                continue
            }

            val football = player.level().getEntity(session.footballId)
            if (football !is Football || !football.isAlive) {
                iterator.remove()
                continue
            }

            if (!FootballDribbleAssist.apply(player, football)) {
                iterator.remove()
                continue
            }

            tryPlayDribbleSound(session, player, football, now)
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
