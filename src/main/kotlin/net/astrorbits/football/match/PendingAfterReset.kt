package net.astrorbits.football.match

import net.minecraft.world.phys.Vec3
import java.util.*

/** 足球延迟复位完成后触发的开球阶段。 */
sealed interface PendingAfterReset {
    val kickoffTeam: TeamSide

    data class PostGoal(override val kickoffTeam: TeamSide) : PendingAfterReset

    data class GoalLineOut(
        override val kickoffTeam: TeamSide,
        val outType: GoalLineOutType? = null,
        val ballPos: Vec3? = null,
        val defendingSide: TeamSide? = null,
        val throwInDirectGoalRestrict: Boolean = false,
        /** 复位后写入新足球的 [net.astrorbits.football.Football.lastPhysicalTouch]（如任意球犯规球员）。 */
        val lastTouchPlayerUuid: UUID? = null,
    ) : PendingAfterReset

    /** 正赛点球：球复位至点球点后进入 [MatchPenaltyKickState]。 */
    data class MatchPenaltyKick(
        override val kickoffTeam: TeamSide,
        val defendingTeam: TeamSide,
        val preferredKickerUuid: UUID?,
        val lastTouchPlayerUuid: UUID?,
    ) : PendingAfterReset
}
