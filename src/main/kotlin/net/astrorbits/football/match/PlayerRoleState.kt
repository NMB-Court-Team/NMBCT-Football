package net.astrorbits.football.match

import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.util.GoalkeeperUtil
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PlayerRoleState {
    var teamAGoalkeeper: UUID? = null
    var teamBGoalkeeper: UUID? = null
    val voluntaryGkMode: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    /** 点球主罚轮次中临时按场外球员操作（仍保留正式门将登记）。 */
    private val penaltyKickOutfieldOverride: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun isDesignatedGoalkeeper(player: ServerPlayer): Boolean {
        val uuid = player.uuid
        return uuid == teamAGoalkeeper || uuid == teamBGoalkeeper || voluntaryGkMode.contains(uuid)
    }

    fun isGoalkeeper(player: ServerPlayer): Boolean {
        if (!MatchState.isDuringMatch()) return false
        if (penaltyKickOutfieldOverride.contains(player.uuid)) return false
        return isDesignatedGoalkeeper(player)
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

    /**
     * 开赛时分配守门员：若该队已有正式门将登记（含上一场保留或 `setGk`）且仍在名单中，则沿用；
     * 否则从该队在线队员中随机一名设为守门员。
     */
    fun assignGoalkeepersOnMatchStart(server: MinecraftServer) {
        for (team in TeamSide.entries) {
            val roster = when (team) {
                TeamSide.A -> MatchState.teamAPlayers
                TeamSide.B -> MatchState.teamBPlayers
            }
            if (roster.isEmpty()) continue

            val preset = when (team) {
                TeamSide.A -> teamAGoalkeeper
                TeamSide.B -> teamBGoalkeeper
            }
            if (preset != null && roster.contains(preset)) {
                server.playerList.getPlayer(preset)?.let { syncRoleToPlayer(it) }
                continue
            }

            roster.shuffled().firstNotNullOfOrNull { server.playerList.getPlayer(it) }?.let { player ->
                setOfficialGk(team, player)
            }
        }
    }

    /** 主罚点球时切换为场外操作（门将/自愿门将）。 */
    fun enterPenaltyKickOutfield(player: ServerPlayer) {
        if (!isDesignatedGoalkeeper(player)) return
        if (!penaltyKickOutfieldOverride.add(player.uuid)) return
        syncRoleToPlayer(player)
    }

    /** 本脚主罚结束后恢复门将操作（若仍为正式门将）。 */
    fun releasePenaltyKickOutfield(player: ServerPlayer) {
        if (!penaltyKickOutfieldOverride.remove(player.uuid)) return
        syncRoleToPlayer(player)
    }

    fun clearPenaltyKickOutfieldOverrides(server: MinecraftServer? = null) {
        if (penaltyKickOutfieldOverride.isEmpty()) return
        val affected = penaltyKickOutfieldOverride.toList()
        penaltyKickOutfieldOverride.clear()
        server ?: return
        for (uuid in affected) {
            server.playerList.getPlayer(uuid)?.let { syncRoleToPlayer(it) }
        }
    }

    /** 复位比赛进程时调用：保留正式门将登记，仅清除临时门将状态。 */
    fun reset() {
        voluntaryGkMode.clear()
        penaltyKickOutfieldOverride.clear()
    }
}
