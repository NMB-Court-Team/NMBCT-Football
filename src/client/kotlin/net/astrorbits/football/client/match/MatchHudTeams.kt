package net.astrorbits.football.client.match

import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.TeamSide
import net.minecraft.network.chat.Component

/** 事件 HUD 队名：优先专用状态，其次 MatchState，最后默认翻译。 */
object MatchHudTeams {
    fun name(side: TeamSide): String {
        val dedicated = when (side) {
            TeamSide.A -> MatchStartClient.teamAName
            TeamSide.B -> MatchStartClient.teamBName
        }
        if (dedicated.isNotBlank()) return dedicated

        val fromState = MatchState.getTeamName(side).string
        if (fromState.isNotBlank()) return fromState

        return Component.translatable(
            when (side) {
                TeamSide.A -> "team_name.nmbct-football.teamA"
                TeamSide.B -> "team_name.nmbct-football.teamB"
            },
        ).string
    }
}
