package net.astrorbits.football.match

import net.minecraft.world.entity.player.Player
import java.util.UUID

/** 球门球 [GoalKickPhase.PLACED]：按水平距离选取发球方距球最近球员。 */
object GoalKickPlacedKickerUtil {
    fun findClosestParticipatingPlayer(
        players: Iterable<Player>,
        restartTeam: TeamSide,
        ballX: Double,
        ballZ: Double,
        teamOf: (Player) -> TeamSide?,
        isParticipating: (Player) -> Boolean,
    ): UUID? {
        var closestUuid: UUID? = null
        var closestDistSq = Double.MAX_VALUE
        for (player in players) {
            if (!isParticipating(player)) continue
            if (teamOf(player) != restartTeam) continue
            val distSq = horizontalDistanceSq(player.x, player.z, ballX, ballZ)
            if (distSq < closestDistSq) {
                closestDistSq = distSq
                closestUuid = player.uuid
            }
        }
        return closestUuid
    }

    fun horizontalDistanceSq(x1: Double, z1: Double, x2: Double, z2: Double): Double {
        val dx = x1 - x2
        val dz = z1 - z2
        return dx * dx + dz * dz
    }
}
