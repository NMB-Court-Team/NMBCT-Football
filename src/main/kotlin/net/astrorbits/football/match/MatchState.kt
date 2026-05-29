package net.astrorbits.football.match

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import java.util.UUID

object MatchState {
	private val DEFAULT_TEAM_A_NAME = Component.translatable("team_name.nmbct-football.teamA")
	private val DEFAULT_TEAM_B_NAME = Component.translatable("team_name.nmbct-football.teamB")

	private const val SCOREBOARD_TEAM_A = "football_A"
	private const val SCOREBOARD_TEAM_B = "football_B"

	var timerTicks = 0
	var isRunning = true
	var teamAName: Component = DEFAULT_TEAM_A_NAME
	var teamBName: Component = DEFAULT_TEAM_B_NAME
	var teamAScore = 0
	var teamBScore = 0
	val teamAPlayers: MutableSet<UUID> = mutableSetOf()
	val teamBPlayers: MutableSet<UUID> = mutableSetOf()

	fun getTeamName(team: TeamSide): Component = when (team) {
		TeamSide.A -> teamAName
		TeamSide.B -> teamBName
	}

	fun getPlayerTeam(uuid: UUID): TeamSide? = when {
		teamAPlayers.contains(uuid) -> TeamSide.A
		teamBPlayers.contains(uuid) -> TeamSide.B
		else -> null
	}

	fun addPlayer(team: TeamSide, uuid: UUID) {
		when (team) {
			TeamSide.A -> teamAPlayers.add(uuid)
			TeamSide.B -> teamBPlayers.add(uuid)
		}
	}

	fun removePlayer(uuid: UUID): Boolean {
		return teamAPlayers.remove(uuid) || teamBPlayers.remove(uuid)
	}

	fun syncPlayerScoreboard(uuid: UUID, team: TeamSide?, server: MinecraftServer) {
		val player = server.playerList.getPlayer(uuid) ?: return
		val playerName = player.gameProfile.name
		val scoreboard = server.scoreboard

		// 从所有足球队伍中移除
		scoreboard.getPlayerTeam(SCOREBOARD_TEAM_A)?.let { scoreboard.removePlayerFromTeam(playerName, it) }
		scoreboard.getPlayerTeam(SCOREBOARD_TEAM_B)?.let { scoreboard.removePlayerFromTeam(playerName, it) }

		if (team != null) {
			val teamKey = when (team) {
				TeamSide.A -> SCOREBOARD_TEAM_A
				TeamSide.B -> SCOREBOARD_TEAM_B
			}
			val color = when (team) {
				TeamSide.A -> ChatFormatting.RED
				TeamSide.B -> ChatFormatting.BLUE
			}
			val sbTeam = scoreboard.getPlayerTeam(teamKey) ?: run {
				val t = scoreboard.addPlayerTeam(teamKey)
				t.setColor(color)
                t.displayName = getTeamName(team)
				t
			}
			scoreboard.addPlayerToTeam(playerName, sbTeam)
		}
	}

	fun clearScoreboardTeams(server: MinecraftServer) {
		val scoreboard = server.scoreboard
		for (teamKey in listOf(SCOREBOARD_TEAM_A, SCOREBOARD_TEAM_B)) {
			scoreboard.getPlayerTeam(teamKey)?.let { team ->
				val players = team.players.toList()
				for (playerName in players) {
					scoreboard.removePlayerFromTeam(playerName, team)
				}
			}
		}
	}

	fun reset() {
		timerTicks = 0
		isRunning = true
		teamAScore = 0
		teamBScore = 0
		teamAPlayers.clear()
		teamBPlayers.clear()
		PlayerRoleState.reset()
	}

	fun togglePause() {
		isRunning = !isRunning
	}

	fun formatTime(): String {
		val totalSeconds = timerTicks / 20
		val minutes = totalSeconds / 60
		val seconds = totalSeconds % 60
		return "%02d:%02d".format(minutes, seconds)
	}
}
