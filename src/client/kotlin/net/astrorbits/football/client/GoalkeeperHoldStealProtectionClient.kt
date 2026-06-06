package net.astrorbits.football.client

import net.astrorbits.football.Football
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.astrorbits.football.match.MatchState
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer

/**
 * 客户端跟踪守门员持球抢球保护窗口（与 [net.astrorbits.football.Football.isHoldStealProtectedFrom] 对齐），供 HUD 显示。
 */
object GoalkeeperHoldStealProtectionClient {
    private const val MS_PER_TICK = 50L
    private const val SCAN_RANGE = 10.0

    private data class HoldProtection(
        var holderId: Int,
        var protectionEndGameTime: Long,
        var protectionEndWallMs: Long,
    )

    private val protectionByFootballId = mutableMapOf<Int, HoldProtection>()
    private val currentHolderByFootballId = mutableMapOf<Int, Int>()

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            tick(client.player)
        }
    }

    fun onMatchReset() {
        protectionByFootballId.clear()
        currentHolderByFootballId.clear()
    }

    /** 本地守门员刚进入持球时调用，避免实体同步延迟导致 HUD 偏短。 */
    fun onGoalkeeperBeganHolding(player: LocalPlayer, gameTime: Long) {
        val protectionTicks = GoalkeeperInputConfig.GK_HOLD_STEAL_PROTECTION_TICKS
        if (protectionTicks <= 0) {
            return
        }
        val football = player.level().getEntitiesOfClass(
            Football::class.java,
            player.boundingBox.inflate(SCAN_RANGE),
        ).firstOrNull { it.getHolderEntityId() == player.id } ?: return

        applyProtection(football.id, player.id, gameTime, protectionTicks)
    }

    fun shouldShowHud(player: LocalPlayer): Boolean {
        if (!MatchState.isDuringMatch() || GoalkeeperInputConfig.GK_HOLD_STEAL_PROTECTION_TICKS <= 0) {
            return false
        }
        val level = player.level()
        val now = level.gameTime
        val rangeSqr = FootballInputConfig.PLAYER_KICK_RANGE * FootballInputConfig.PLAYER_KICK_RANGE

        for ((footballId, entry) in protectionByFootballId) {
            if (!isProtectionActive(entry, now)) {
                continue
            }
            if (player.id == entry.holderId) {
                return true
            }
            val football = level.getEntity(footballId) as? Football ?: continue
            if (player.distanceToSqr(football) <= rangeSqr) {
                return true
            }
        }
        return false
    }

    private fun tick(player: LocalPlayer?) {
        if (player == null || !MatchState.isDuringMatch()) {
            protectionByFootballId.clear()
            return
        }

        val protectionTicks = GoalkeeperInputConfig.GK_HOLD_STEAL_PROTECTION_TICKS
        if (protectionTicks <= 0) {
            protectionByFootballId.clear()
            return
        }

        val level = player.level()
        val now = level.gameTime
        val seen = mutableSetOf<Int>()

        for (football in level.getEntitiesOfClass(
            Football::class.java,
            player.boundingBox.inflate(SCAN_RANGE),
        )) {
            val holderId = football.getHolderEntityId()
            if (holderId < 0) {
                continue
            }
            seen += football.id
            val previousHolder = currentHolderByFootballId[football.id]
            currentHolderByFootballId[football.id] = holderId
            val existing = protectionByFootballId[football.id]
            if (previousHolder != holderId) {
                applyProtection(football.id, holderId, now, protectionTicks)
            }
        }

        currentHolderByFootballId.keys.retainAll(seen)
        protectionByFootballId.keys.retainAll(currentHolderByFootballId.keys)
        protectionByFootballId.entries.removeIf { (_, entry) -> !isProtectionActive(entry, now) }
    }

    private fun applyProtection(footballId: Int, holderId: Int, gameTime: Long, protectionTicks: Int) {
        val endGame = gameTime + protectionTicks
        val endWall = System.currentTimeMillis() + protectionTicks * MS_PER_TICK
        val existing = protectionByFootballId[footballId]
        if (existing == null || endGame > existing.protectionEndGameTime) {
            protectionByFootballId[footballId] = HoldProtection(holderId, endGame, endWall)
        } else if (existing.holderId != holderId) {
            protectionByFootballId[footballId] = HoldProtection(holderId, endGame, endWall)
        }
    }

    private fun isProtectionActive(entry: HoldProtection, now: Long): Boolean {
        if (now < entry.protectionEndGameTime) {
            return true
        }
        return entry.protectionEndWallMs > 0L && System.currentTimeMillis() < entry.protectionEndWallMs
    }
}
