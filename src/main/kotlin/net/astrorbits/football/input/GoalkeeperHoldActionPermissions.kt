package net.astrorbits.football.input

import net.astrorbits.football.network.FootballNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 运行时控制球员捡球 / 放下 / 抛出权限（默认全部允许）。
 * 供罚球、球门球等流程在服务端快速开关，并同步到对应客户端 HUD 与输入。
 */
object GoalkeeperHoldActionPermissions {
    private data class Flags(
        val catch: Boolean = true,
        val drop: Boolean = true,
        val throwBall: Boolean = true,
    ) {
        companion object {
            val ALL_ENABLED = Flags()
        }
    }

    private val perPlayer = ConcurrentHashMap<UUID, Flags>()

    fun canCatch(player: ServerPlayer): Boolean = flags(player).catch

    fun canDrop(player: ServerPlayer): Boolean = flags(player).drop

    fun canThrow(player: ServerPlayer): Boolean = flags(player).throwBall

    fun setCanCatch(player: ServerPlayer, enabled: Boolean) {
        update(player) { it.copy(catch = enabled) }
    }

    fun setCanDrop(player: ServerPlayer, enabled: Boolean) {
        update(player) { it.copy(drop = enabled) }
    }

    fun setCanThrow(player: ServerPlayer, enabled: Boolean) {
        update(player) { it.copy(throwBall = enabled) }
    }

    fun setAll(
        player: ServerPlayer,
        catch: Boolean = true,
        drop: Boolean = true,
        throwBall: Boolean = true,
    ) {
        val next = Flags(catch = catch, drop = drop, throwBall = throwBall)
        perPlayer[player.uuid] = next
        syncToPlayer(player, next)
    }

    fun resetToDefaults(player: ServerPlayer) {
        perPlayer.remove(player.uuid)
        syncToPlayer(player, Flags.ALL_ENABLED)
    }

    /** 将当前权限状态同步到客户端（玩家加入或重连时调用）。 */
    fun syncToClient(player: ServerPlayer) {
        syncToPlayer(player, flags(player))
    }

    fun clearAll(server: MinecraftServer) {
        if (perPlayer.isEmpty()) {
            return
        }
        val affected = perPlayer.keys.toList()
        perPlayer.clear()
        for (uuid in affected) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            syncToPlayer(player, Flags.ALL_ENABLED)
        }
    }

    private fun flags(player: ServerPlayer): Flags = perPlayer[player.uuid] ?: Flags.ALL_ENABLED

    private fun update(player: ServerPlayer, transform: (Flags) -> Flags) {
        val next = transform(flags(player))
        perPlayer[player.uuid] = next
        syncToPlayer(player, next)
    }

    private fun syncToPlayer(player: ServerPlayer, flags: Flags) {
        FootballNetworking.sendGoalkeeperHoldActionPermissions(
            player = player,
            canCatch = flags.catch,
            canDrop = flags.drop,
            canThrow = flags.throwBall,
        )
    }
}
