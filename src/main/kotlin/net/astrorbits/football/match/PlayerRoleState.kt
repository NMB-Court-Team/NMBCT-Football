package net.astrorbits.football.match

import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.util.GoalkeeperUtil
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PlayerRoleState {
    var teamAGoalkeeper: UUID? = null
    var teamBGoalkeeper: UUID? = null
    val voluntaryGkMode: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun isGoalkeeper(player: ServerPlayer): Boolean {
        val uuid = player.uuid
        return uuid == teamAGoalkeeper || uuid == teamBGoalkeeper || voluntaryGkMode.contains(uuid)
    }

    fun setOfficialGk(team: TeamSide, player: ServerPlayer?) {
        val previous = when (team) {
            TeamSide.A -> {
                val old = teamAGoalkeeper
                teamAGoalkeeper = player?.uuid
                old
            }
            TeamSide.B -> {
                val old = teamBGoalkeeper
                teamBGoalkeeper = player?.uuid
                old
            }
        }

        player?.let { voluntaryGkMode.remove(it.uuid) }
        val server = player?.level()?.server
        previous?.let { uuid ->
            FootballNetworking.syncGoalkeeperRole(uuid, server)
        }
        player?.let { syncRoleToPlayer(it) }
    }

    fun clearOfficialGk(team: TeamSide, server: net.minecraft.server.MinecraftServer?) {
        val previous = when (team) {
            TeamSide.A -> teamAGoalkeeper.also { teamAGoalkeeper = null }
            TeamSide.B -> teamBGoalkeeper.also { teamBGoalkeeper = null }
        }
        previous?.let { FootballNetworking.syncGoalkeeperRole(it, server) }
    }

    fun setVoluntaryGk(player: ServerPlayer, enabled: Boolean): Boolean {
        if (enabled) {
            voluntaryGkMode.add(player.uuid)
        } else {
            voluntaryGkMode.remove(player.uuid)
        }
        syncRoleToPlayer(player)
        return enabled
    }

    fun syncRoleToPlayer(player: ServerPlayer) {
        val isGk = isGoalkeeper(player)
        FootballNetworking.sendGoalkeeperRole(player, isGk)
        if (!isGk) {
            releaseHeldFootballOnRoleExit(player)
        }
    }

    /** 退出守门员身份时放下手中足球，避免球仍粘在实体上却无法操作。 */
    private fun releaseHeldFootballOnRoleExit(player: ServerPlayer) {
        GoalkeeperUtil.findHeldFootball(player)?.dropAt(player)
    }

    /** 从双方队伍中各随机选取一名在线队员设为守门员。已离线的队员会被跳过。 */
    fun randomAssignGoalkeepers(server: net.minecraft.server.MinecraftServer) {
        for (team in TeamSide.entries) {
            val players = when (team) {
                TeamSide.A -> MatchState.teamAPlayers.toList()
                TeamSide.B -> MatchState.teamBPlayers.toList()
            }
            if (players.isEmpty()) continue
            players.shuffled().firstNotNullOfOrNull { server.playerList.getPlayer(it) }?.let { player ->
                setOfficialGk(team, player)
            }
        }
    }

    fun reset() {
        teamAGoalkeeper = null
        teamBGoalkeeper = null
        voluntaryGkMode.clear()
    }
}
