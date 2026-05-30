package net.astrorbits.football.client

import net.astrorbits.football.Football
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.level.Level

object FootballOperabilityClient {
    fun canOperateFootball(player: LocalPlayer, level: Level): Boolean {
        if (MatchStartClient.isLocked) return false
        if (GoalkeeperStateClient.isGoalkeeper) {
            if (GoalkeeperStateClient.isHoldingBall) {
                return !GoalkeeperStateClient.isHoldReleaseLocked()
            }
            val range = GoalkeeperInputConfig.GK_CATCH_RANGE + GoalkeeperInputConfig.GK_CROUCH_RANGE_BONUS
            return nearestOperableFootball(player, level, range) != null
        }

        return nearestOperableFootball(player, level, FootballInputConfig.PLAYER_KICK_RANGE) != null
    }

    fun nearestOperableFootball(player: LocalPlayer, level: Level, range: Double): Football? {
        return level.getEntitiesOfClass(
            Football::class.java,
            player.boundingBox.inflate(range),
        )
            .filter { !it.isHeld() && player.distanceToSqr(it) <= range * range }
            .minByOrNull { it.distanceToSqr(player) }
    }
}
