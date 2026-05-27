package net.astrorbits.football.input

import net.astrorbits.football.Football
import net.astrorbits.football.match.PlayerRoleState
import net.astrorbits.football.util.FootballKickUtil
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class DribbleSession(
    val playerId: UUID,
    val footballId: Int,
    var lastHeartbeatTick: Long,
)

object FootballDribbleSessions {
    private val sessions = ConcurrentHashMap<UUID, DribbleSession>()

    fun registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    /**
     * @return true 表示新建 session（可播运球音效）
     */
    fun beginOrRefresh(player: ServerPlayer, football: Football, now: Long): Boolean {
        if (PlayerRoleState.isGoalkeeper(player)) {
            return false
        }

        val existing = sessions[player.uuid]
        if (existing == null || existing.footballId != football.id) {
            sessions[player.uuid] = DribbleSession(
                playerId = player.uuid,
                footballId = football.id,
                lastHeartbeatTick = now,
            )
            if (FootballInputConfig.DRIBBLE_TOUCH_FORCE > 0.0) {
                FootballKickUtil.applyDribbleTouch(player, football)
            }
            return true
        }

        existing.lastHeartbeatTick = now
        return false
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
            }
        }
    }
}
