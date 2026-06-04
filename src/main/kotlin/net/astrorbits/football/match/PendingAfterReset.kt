package net.astrorbits.football.match

/** 足球延迟复位完成后触发的开球阶段。 */
sealed interface PendingAfterReset {
    val kickoffTeam: TeamSide

    data class PostGoal(override val kickoffTeam: TeamSide) : PendingAfterReset

    data class GoalLineOut(
        override val kickoffTeam: TeamSide,
        val throwInDirectGoalRestrict: Boolean = false,
    ) : PendingAfterReset
}
