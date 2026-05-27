package net.astrorbits.football.input

import net.astrorbits.football.util.FootballKickUtil
import net.astrorbits.football.util.GoalkeeperUtil
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class DiveSession(
    val playerId: UUID,
    val direction: Vec3,
    val startTick: Long,
    var resolved: Boolean = false,
)

object GoalkeeperDiveSessions {
    private val sessions = ConcurrentHashMap<UUID, DiveSession>()

    fun registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    fun begin(player: ServerPlayer, direction: Vec3, now: Long) {
        sessions[player.uuid] = DiveSession(
            playerId = player.uuid,
            direction = direction,
            startTick = now,
        )
    }

    fun isDiving(player: ServerPlayer): Boolean = sessions.containsKey(player.uuid)

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

            val elapsed = now - session.startTick
            if (elapsed >= GoalkeeperInputConfig.GK_DIVE_DURATION_TICKS) {
                iterator.remove()
                continue
            }

            applyDiveMovement(player, session.direction)

            if (!session.resolved) {
                tryResolveSave(player, session)
            }
        }
    }

    private fun applyDiveMovement(player: ServerPlayer, direction: Vec3) {
        val motion = direction.scale(GoalkeeperInputConfig.GK_DIVE_SPEED)
        player.setDeltaMovement(
            player.deltaMovement.x + motion.x,
            player.deltaMovement.y,
            player.deltaMovement.z + motion.z,
        )
    }

    private fun tryResolveSave(player: ServerPlayer, session: DiveSession) {
        val range = GoalkeeperUtil.diveRange(player)
        val football = FootballKickUtil.findNearestFootball(player, range) ?: return
        if (football.isHeld()) {
            return
        }

        val origin = player.position().add(0.0, player.eyeHeight * 0.5, 0.0)
        if (!GoalkeeperUtil.isInDirectionalSector(
                origin,
                session.direction,
                GoalkeeperUtil.ballCenter(football),
                range,
                GoalkeeperInputConfig.GK_DIVE_HALF_ANGLE_DEG,
            )
        ) {
            return
        }

        session.resolved = true
        GoalkeeperActions.tryResolveDiveCatch(player, football, session.direction)
        sessions.remove(player.uuid)
    }
}
