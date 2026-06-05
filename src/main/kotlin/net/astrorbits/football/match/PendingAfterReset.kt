package net.astrorbits.football.match

import java.util.UUID

/** 足球延迟复位完成后触发的开球阶段。 */
sealed interface PendingAfterReset {
    val kickoffTeam: TeamSide

    data class PostGoal(override val kickoffTeam: TeamSide) : PendingAfterReset

    data class GoalLineOut(
        override val kickoffTeam: TeamSide,
        val throwInDirectGoalRestrict: Boolean = false,
        /** 复位后写入新足球的 [net.astrorbits.football.Football.lastPhysicalTouch]（如任意球犯规球员）。 */
        val lastTouchPlayerUuid: UUID? = null,
    ) : PendingAfterReset
}
