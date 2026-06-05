package net.astrorbits.football.match

import net.astrorbits.football.input.GoalkeeperHoldActionPermissions
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.util.GoalkeeperUtil
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PlayerRoleState {
    var teamAGoalkeeper: UUID? = null
    var teamBGoalkeeper: UUID? = null
    /** 点球主罚轮次中临时按场外球员操作（仍保留正式门将登记）。 */
    private val penaltyKickOutfieldOverride: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun isDesignatedGoalkeeper(player: ServerPlayer): Boolean {
        val uuid = player.uuid
        return uuid == teamAGoalkeeper || uuid == teamBGoalkeeper
    }

    fun isGoalkeeper(player: ServerPlayer): Boolean {
        if (!MatchParticipation.isParticipating(player)) return false
        if (penaltyKickOutfieldOverride.contains(player.uuid)) return false
        if (!MatchState.allowsActiveGoalkeeperRole()) return false
        val uuid = player.uuid
        return uuid == teamAGoalkeeper || uuid == teamBGoalkeeper
    }

    fun setOfficialGk(team: TeamSide, player: ServerPlayer, server: MinecraftServer): Boolean {
        if (!MatchParticipation.isParticipating(player)) {
            return false
        }
        val playerUuid = player.uuid
        clearOfficialGkSlotIfHeldBy(team.opponent(), playerUuid)

        val previous = when (team) {
            TeamSide.A -> teamAGoalkeeper.also { teamAGoalkeeper = playerUuid }
            TeamSide.B -> teamBGoalkeeper.also { teamBGoalkeeper = playerUuid }
        }

        if (previous != null && previous != playerUuid) {
            FootballNetworking.syncGoalkeeperRole(previous, server)
        }
        syncRoleToPlayer(player)
        return true
    }

    fun clearOfficialGk(team: TeamSide, server: MinecraftServer?) {
        val previous = when (team) {
            TeamSide.A -> teamAGoalkeeper.also { teamAGoalkeeper = null }
            TeamSide.B -> teamBGoalkeeper.also { teamBGoalkeeper = null }
        }
        previous?.let { FootballNetworking.syncGoalkeeperRole(it, server) }
    }

    private fun clearOfficialGkSlotIfHeldBy(team: TeamSide, uuid: UUID) {
        when (team) {
            TeamSide.A -> if (teamAGoalkeeper == uuid) teamAGoalkeeper = null
            TeamSide.B -> if (teamBGoalkeeper == uuid) teamBGoalkeeper = null
        }
    }

    fun syncRoleToPlayer(player: ServerPlayer) {
        val isGk = isGoalkeeper(player)
        FootballNetworking.sendGoalkeeperRole(player, isGk)
        GoalkeeperHoldActionPermissions.syncToClient(player)
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
    /** 沿用已登记且仍在名单中的门将；否则从该队在线队员中随机一名。 */
    fun assignGoalkeepersIfMissing(server: MinecraftServer) {
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
                val online = server.playerList.getPlayer(preset)
                when {
                    online == null -> continue
                    MatchParticipation.isParticipating(online) -> {
                        syncRoleToPlayer(online)
                        continue
                    }
                }
            }

            MatchParticipation.filterParticipatingRoster(roster, server)
                .shuffled()
                .firstNotNullOfOrNull { server.playerList.getPlayer(it) }
                ?.let { player -> setOfficialGk(team, player, server) }
        }
    }

    fun assignGoalkeepersOnMatchStart(server: MinecraftServer) = assignGoalkeepersIfMissing(server)

    /** 主罚点球时切换为场外操作（门将）。 */
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

    /** 复位比赛进程时调用：保留正式门将登记，仅清除点球主罚时的场外操作覆盖。 */
    fun reset() {
        penaltyKickOutfieldOverride.clear()
    }
}
