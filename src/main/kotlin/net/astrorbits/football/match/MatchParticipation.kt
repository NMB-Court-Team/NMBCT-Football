package net.astrorbits.football.match

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import java.util.*

/**
 * 比赛参与身份：旁观模式玩家可保留队伍登记与 HUD，但不参与选人、站位、开球队伍判定与足球操作。
 */
object MatchParticipation {
    fun isSpectator(player: Player): Boolean = player.isSpectator

    fun isParticipating(player: Player): Boolean = !player.isSpectator

    /** 定位球/点球主罚等选人：在场参赛且未处于罚下状态。 */
    fun isEligibleForSetPiece(player: Player): Boolean =
        isParticipating(player) && !MatchSendOffState.isSentOff(player.uuid)

    /** 开球锁、触球解锁、点球主罚等使用的有效队伍；旁观者返回 null。 */
    fun participatingTeam(player: ServerPlayer): TeamSide? =
        if (isSpectator(player)) null else MatchState.getPlayerTeam(player.uuid)

    fun participatingTeam(server: MinecraftServer, uuid: UUID): TeamSide? {
        val player = server.playerList.getPlayer(uuid) ?: return MatchState.getPlayerTeam(uuid)
        return participatingTeam(player)
    }

    fun filterParticipatingRoster(uuids: Collection<UUID>, server: MinecraftServer): List<UUID> =
        uuids.filter { uuid ->
            if (MatchSendOffState.isSentOff(uuid)) return@filter false
            val online = server.playerList.getPlayer(uuid) ?: return@filter true
            isParticipating(online)
        }

    fun onlineParticipating(server: MinecraftServer, uuids: Collection<UUID>): List<ServerPlayer> =
        uuids.mapNotNull { server.playerList.getPlayer(it) }.filter { isParticipating(it) }

    fun onlineEligibleForSetPiece(server: MinecraftServer, uuids: Collection<UUID>): List<ServerPlayer> =
        uuids.mapNotNull { server.playerList.getPlayer(it) }.filter { isEligibleForSetPiece(it) }
}
