package net.astrorbits.football.client

import net.astrorbits.football.Football
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.astrorbits.football.match.MatchState
import net.minecraft.client.player.LocalPlayer

/**
 * 客户端抢球保护 HUD：直接读取足球实体同步的持球开始时刻，与服务端 [net.astrorbits.football.Football.isHoldStealProtectionActive] 对齐。
 */
object GoalkeeperHoldStealProtectionClient {
    private const val SCAN_RANGE = 10.0

    fun register() {
        // HUD 每帧查询实体数据，无需 tick 跟踪
    }

    fun onMatchReset() = Unit

    fun shouldShowHud(player: LocalPlayer): Boolean {
        if (!MatchState.isDuringMatch() || GoalkeeperInputConfig.GK_HOLD_STEAL_PROTECTION_TICKS <= 0) {
            return false
        }
        val level = player.level()
        val now = level.gameTime
        val rangeSqr = FootballInputConfig.PLAYER_KICK_RANGE * FootballInputConfig.PLAYER_KICK_RANGE

        for (football in level.getEntitiesOfClass(
            Football::class.java,
            player.boundingBox.inflate(SCAN_RANGE),
        )) {
            if (!football.isHoldStealProtectionActive(now)) {
                continue
            }
            if (player.id == football.getHolderEntityId()) {
                continue
            }
            if (player.distanceToSqr(football) <= rangeSqr) {
                return true
            }
        }
        return false
    }
}
