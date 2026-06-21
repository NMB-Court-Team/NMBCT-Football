package net.astrorbits.football.match

import net.astrorbits.football.input.SlideTackleSessions
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.util.GoalkeeperUtil
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/** 禁区内滑铲犯规：罚下（比赛计时）或中圈开球时回归。 */
object MatchSendOffState {
    private data class Entry(
        val team: TeamSide,
        val expireAtTimerTicks: Int,
        val previousGameMode: GameType,
        val skipNextCenterKickoffRestore: Boolean = false,
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
        FootballNetworking.broadcastPlayerSendOff(
            server,
            player.uuid,
            player.gameProfile.name,
            team,
            expireAt,
        )
        if (durationTicks <= 0) {
            restore(server, player.uuid)
            return
        }
        tryFinishForForfeit(server, team)
    }

    fun skipNextCenterKickoffRestore(uuid: UUID) {
        val entry = sentOff[uuid] ?: return
        sentOff[uuid] = entry.copy(skipNextCenterKickoffRestore = true)
    }

    /** 若该队登记队员全部处于罚下状态，则对手直接获胜。 */
    fun tryFinishForForfeit(server: MinecraftServer, team: TeamSide): Boolean {
        if (!rosterFullySentOff(team)) return false
        if (!MatchState.isDuringMatch()) return false
        finishMatchForForfeit(server, team.opponent())
        return true
    }

    private fun rosterFullySentOff(team: TeamSide): Boolean {
        val roster = when (team) {
            TeamSide.A -> MatchState.teamAPlayers
            TeamSide.B -> MatchState.teamBPlayers
        }
        if (roster.isEmpty()) return false
        return roster.all { isSentOff(it) }
    }

    private fun finishMatchForForfeit(server: MinecraftServer, winner: TeamSide) {
        PenaltyFoulGoalWatchState.clear()
        MatchPenaltyKickState.clear(server)
        MatchState.postGoalResetPending = false
        SetPieceState.clear()
        GoalKickSetPieceFlow.clear(server)
        ThrowInSetPieceFlow.clear(server)
        SetPieceAreaViolationMonitor.clearAll(server)
        MatchState.clearKickoffWhistleTimers()
        MatchState.forfeitWinner = winner
        MatchState.setPhase(MatchPhase.FINISHED, server)
        FootballNetworking.syncTimerToClients(server)
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

    fun restoreAllForCenterKickoff(server: MinecraftServer) {
        for (uuid in sentOff.keys.toList()) {
            val entry = sentOff[uuid] ?: continue
            if (entry.skipNextCenterKickoffRestore) {
                sentOff[uuid] = entry.copy(skipNextCenterKickoffRestore = false)
                continue
            }
            restore(server, uuid, repositionToCorner = true)
        }
    }

    /** 比赛结束或重置：恢复游戏模式，不再视为罚下（不传送）。 */
    fun restoreAllForMatchEnd(server: MinecraftServer) {
        restoreAll(server, repositionToCorner = false)
    }

    private fun restoreAll(server: MinecraftServer, repositionToCorner: Boolean) {
        for (uuid in sentOff.keys.toList()) {
            restore(server, uuid, repositionToCorner)
        }
    }

    fun restore(server: MinecraftServer, uuid: UUID, repositionToCorner: Boolean = true) {
        val entry = sentOff[uuid] ?: return
        val player = server.playerList.getPlayer(uuid)
        if (player == null) {
            sentOff.remove(uuid)
            return
        }
        sentOff.remove(uuid)
        val restoredMode = if (entry.previousGameMode == GameType.SPECTATOR) {
            GameType.ADVENTURE
        } else {
            entry.previousGameMode
        }
        player.setGameMode(restoredMode)
        if (repositionToCorner) {
            MatchState.teleportPlayerToTeamCornerFarFromBall(player, entry.team, server)
        }
        FootballNetworking.sendPlayerSendOffRestore(player)
    }

    fun clear() {
        sentOff.clear()
    }
}
