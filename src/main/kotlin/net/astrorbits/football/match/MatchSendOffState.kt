package net.astrorbits.football.match

import net.astrorbits.football.input.SlideTackleSessions
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.util.GoalkeeperUtil
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/** 禁区内滑铲犯规：罚下（比赛计时）或下次中场开球时回归。 */
object MatchSendOffState {
    private data class Entry(
        val team: TeamSide,
        val expireAtTimerTicks: Int,
        val previousGameMode: GameType,
    )

    private val sentOff = ConcurrentHashMap<UUID, Entry>()

    fun isSentOff(uuid: UUID): Boolean = sentOff.containsKey(uuid)

    fun sendOffDurationTicks(): Int =
        MatchConfigHolder.current.sendOffDurationSeconds.coerceAtLeast(0) * 20

    fun sendOffForSlideTackleFoul(server: MinecraftServer, player: ServerPlayer, team: TeamSide) {
        if (isSentOff(player.uuid)) return
        SlideTackleSessions.end(player)
        GoalkeeperUtil.findHeldFootball(player)?.dropAt(player)
        val previous = player.gameMode.gameModeForPlayer
        val durationTicks = sendOffDurationTicks()
        val expireAt = MatchState.timerTicks + durationTicks
        sentOff[player.uuid] = Entry(
            team = team,
            expireAtTimerTicks = expireAt,
            previousGameMode = previous,
        )
        player.setGameMode(GameType.SPECTATOR)
        teleportToSideline(player)
        FootballNetworking.broadcastPlayerSendOff(
            server,
            player.uuid,
            player.gameProfile.name,
            team,
            expireAt,
        )
        if (durationTicks <= 0) {
            restore(server, player.uuid)
        }
    }

    fun tick(server: MinecraftServer) {
        if (sentOff.isEmpty()) return
        val now = MatchState.timerTicks
        for (uuid in sentOff.keys.toList()) {
            val entry = sentOff[uuid] ?: continue
            if (now >= entry.expireAtTimerTicks) {
                restore(server, uuid)
            }
        }
    }

    fun restoreAllForHalfKickoff(server: MinecraftServer) {
        for (uuid in sentOff.keys.toList()) {
            restore(server, uuid)
        }
    }

    fun restore(server: MinecraftServer, uuid: UUID) {
        val entry = sentOff.remove(uuid) ?: return
        val player = server.playerList.getPlayer(uuid) ?: return
        player.setGameMode(entry.previousGameMode)
        MatchState.teleportPlayerToTeamSpawn(player, entry.team)
        FootballNetworking.sendPlayerSendOffRestore(player)
    }

    fun clear() {
        sentOff.clear()
    }

    private fun teleportToSideline(player: ServerPlayer) {
        val center = MatchConfigHolder.current.kickOff
        player.teleportTo(
            player.level(),
            center.x,
            center.y,
            center.z,
            HashSet(),
            player.yRot,
            player.xRot,
            false,
        )
    }
}
