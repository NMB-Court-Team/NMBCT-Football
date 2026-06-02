package net.astrorbits.football.client

import net.astrorbits.football.Football
import net.astrorbits.football.client.key.FootballInputHandler
import net.astrorbits.football.client.key.FootballKeyBindings
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.minecraft.client.KeyMapping
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.level.Level

object FootballOperabilityClient {
    /** 与 [canUseFootballHint] 一致：是否存在至少一项当前可用的足球操作（用于旧调用方）。 */
    fun canOperateFootball(player: LocalPlayer, level: Level): Boolean {
        if (!canShowFootballHints(player)) {
            return false
        }
        return footballHintKeys(player).any { canUseFootballHint(player, level, it) }
    }

    fun canShowFootballHints(player: LocalPlayer): Boolean =
        player.mainHandItem.isEmpty && !MatchStartClient.isLocked

    /**
     * 右上角单条按键是否应高亮：与客户端实际会处理/发包的条件对齐，而非仅用「附近是否有球」。
     */
    fun canUseFootballHint(player: LocalPlayer, level: Level, key: KeyMapping): Boolean {
        if (!canShowFootballHints(player)) {
            return false
        }

        if (GoalkeeperStateClient.isGoalkeeper) {
            if (GoalkeeperStateClient.isHoldingBall) {
                return !GoalkeeperStateClient.isHoldReleaseLocked()
            }
            return when (key) {
                FootballKeyBindings.KICK -> true
                FootballKeyBindings.DRIBBLE -> FootballInputHandler.isGoalkeeperDiveChargeActive()
                FootballKeyBindings.TRAP -> hasBallWithinRange(player, level, goalkeeperCatchRange(player))
                FootballKeyBindings.CHIP -> hasBallWithinRange(player, level, goalkeeperPunchRange(player))
                else -> false
            }
        }

        return when (key) {
            FootballKeyBindings.KICK,
            FootballKeyBindings.DRIBBLE,
            FootballKeyBindings.TRAP,
            FootballKeyBindings.CHIP,
            -> hasBallWithinRange(player, level, FootballInputConfig.PLAYER_KICK_RANGE)
            else -> false
        }
    }

    private fun footballHintKeys(player: LocalPlayer): List<KeyMapping> {
        if (GoalkeeperStateClient.isGoalkeeper) {
            return if (GoalkeeperStateClient.isHoldingBall) {
                listOf(FootballKeyBindings.KICK, FootballKeyBindings.TRAP)
            } else {
                val keys = mutableListOf(
                    FootballKeyBindings.KICK,
                    FootballKeyBindings.TRAP,
                    FootballKeyBindings.CHIP,
                )
                if (FootballInputHandler.isGoalkeeperDiveChargeActive()) {
                    keys += FootballKeyBindings.DRIBBLE
                }
                keys
            }
        }
        return listOf(
            FootballKeyBindings.KICK,
            FootballKeyBindings.DRIBBLE,
            FootballKeyBindings.TRAP,
            FootballKeyBindings.CHIP,
        )
    }

    private fun goalkeeperCatchRange(player: LocalPlayer): Double {
        var range = GoalkeeperInputConfig.GK_CATCH_RANGE
        if (player.isShiftKeyDown) {
            range += GoalkeeperInputConfig.GK_CROUCH_RANGE_BONUS
        }
        return range
    }

    private fun goalkeeperPunchRange(player: LocalPlayer): Double {
        var range = GoalkeeperInputConfig.GK_PUNCH_RANGE
        if (player.isShiftKeyDown) {
            range += GoalkeeperInputConfig.GK_CROUCH_RANGE_BONUS
        }
        return range
    }

    private fun hasBallWithinRange(player: LocalPlayer, level: Level, range: Double): Boolean =
        nearestOperableFootball(player, level, range) != null

    fun nearestOperableFootball(player: LocalPlayer, level: Level, range: Double): Football? {
        return level.getEntitiesOfClass(
            Football::class.java,
            player.boundingBox.inflate(range),
        )
            .filter { !it.isHeld() && !it.isPlayerBallMovementForbidden(player) && player.distanceToSqr(it) <= range * range }
            .minByOrNull { it.distanceToSqr(player) }
    }
}
