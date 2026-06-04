package net.astrorbits.football.match

/** 足球延迟复位完成后触发的开球阶段。 */
sealed interface PendingAfterReset {
    val kickoffTeam: TeamSide

    data class PostGoal(override val kickoffTeam: TeamSide) : PendingAfterReset

    data class GoalLineOut(
        override val kickoffTeam: TeamSide,
        val throwInDirectGoalRestrict: Boolean = false,
    ) : PendingAfterReset

    /** 无效进球后重新开球（保持掷界外球/半场开球的直接进球限制）。 */
    data class DirectGoalInvalidReset(
        override val kickoffTeam: TeamSide,
        val restartKind: DirectGoalRestartKind,
    ) : PendingAfterReset
}
