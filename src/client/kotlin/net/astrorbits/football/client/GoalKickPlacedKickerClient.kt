package net.astrorbits.football.client

import net.astrorbits.football.Football
import net.astrorbits.football.match.GoalKickPhase
import net.astrorbits.football.match.GoalKickPlacedKickerUtil
import net.astrorbits.football.match.MatchParticipation
import net.astrorbits.football.match.SetPieceKind
import net.astrorbits.football.match.TeamSide
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID

object GoalKickPlacedKickerClient {
    fun isPlacedKicker(player: LocalPlayer): Boolean {
        if (SetPieceClient.kind != SetPieceKind.GOAL_KICK) return false
        if (SetPieceClient.goalKickPhase != GoalKickPhase.PLACED) return false
        val restartTeam = SetPieceClient.restartTeam ?: return false
        if (FootballOperabilityClient.resolveLocalPlayerTeam(player) != restartTeam) return false
        val ballPos = ballPosition(player.level()) ?: return false
        val closest = findClosestRestartTeamPlayer(player.level(), restartTeam, ballPos.x, ballPos.z)
            ?: return false
        return player.uuid == closest
    }

    fun findClosestRestartTeamPlayer(level: Level, restartTeam: TeamSide, ballX: Double, ballZ: Double): UUID? =
        GoalKickPlacedKickerUtil.findClosestParticipatingPlayer(
            players = level.players(),
            restartTeam = restartTeam,
            ballX = ballX,
            ballZ = ballZ,
            teamOf = { player -> resolvePlayerTeam(player) },
            isParticipating = MatchParticipation::isParticipating,
        )

    fun ballPosition(level: Level): Vec3? {
        val anchor = SetPieceClient.ballPos ?: return null
        val box = AABB.ofSize(anchor, 8.0, 8.0, 8.0)
        val football = level.getEntitiesOfClass(Football::class.java, box)
            .minByOrNull { it.distanceToSqr(anchor) }
        return if (football != null) {
            Vec3(football.x, football.y, football.z)
        } else {
            anchor
        }
    }

    private fun resolvePlayerTeam(player: Player): TeamSide? {
        val sbTeam = player.level().scoreboard.getPlayersTeam(player.gameProfile.name) ?: return null
        return when (sbTeam.name) {
            SCOREBOARD_TEAM_A -> TeamSide.A
            SCOREBOARD_TEAM_B -> TeamSide.B
            else -> null
        }
    }

    private const val SCOREBOARD_TEAM_A = "football_A"
    private const val SCOREBOARD_TEAM_B = "football_B"
}
