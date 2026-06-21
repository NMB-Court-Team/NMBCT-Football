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
        tryFinishForForfeit(server, team)
        if (durationTicks <= 0 && MatchState.currentPhase != MatchPhase.FINISHED) {
            restore(server, player.uuid)
        }
    }

    fun skipNextCenterKickoffRestore(uuid: UUID) {
        val entry = sentOff[uuid] ?: return
        sentOff[uuid] = entry.copy(skipNextCenterKickoffRestore = true)
    }

    /** 若该队已无人在场可踢且至少一人处于罚下，则对手直接获胜。 */
    fun tryFinishForForfeit(server: MinecraftServer, team: TeamSide): Boolean {
        if (!teamForfeitsDueToSendOff(server, team)) return false
        if (!MatchState.isDuringMatch()) return false
        finishMatchForForfeit(server, team.opponent())
        return true
    }

    /** 该队所有在线队员均被罚下（或不在线），且至少一名队员处于罚下状态。 */
    private fun teamForfeitsDueToSendOff(server: MinecraftServer, team: TeamSide): Boolean {
        val roster = rosterFor(team)
        if (roster.isEmpty()) return false
        if (!roster.any { isSentOff(it) }) return false
        return roster.none { uuid ->
            val player = server.playerList.getPlayer(uuid) ?: return@none false
            MatchParticipation.isParticipating(player) && !isSentOff(uuid)
        }
    }

    private fun rosterFor(team: TeamSide): Set<UUID> = when (team) {
        TeamSide.A -> MatchState.teamAPlayers
        TeamSide.B -> MatchState.teamBPlayers
    }

    private fun finishMatchForForfeit(server: MinecraftServer, winner: TeamSide) {
        PenaltyFoulGoalWatchState.clear()
        PenaltyShootoutState.clear(server)
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
        FootballNetworking.broadcastMatchResult(
            server,
            MatchState.teamAScore,
            MatchState.teamBScore,
            MatchState.getTeamName(TeamSide.A).string,
            MatchState.getTeamName(TeamSide.B).string,
            isDraw = false,
            forfeitWinner = winner,
        )
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
