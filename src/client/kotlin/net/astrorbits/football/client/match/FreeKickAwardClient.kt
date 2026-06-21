package net.astrorbits.football.client.match

import net.astrorbits.football.match.FreeKickFoulReason
import net.astrorbits.football.match.FreeKickType
import net.astrorbits.football.match.SetPieceKind
import net.astrorbits.football.match.TeamSide

object FreeKickAwardClient {
    private const val DURATION_MS = 4000L
    /** 观察期/延迟复位期间保持 Banner 可见时的固定动画进度。 */
    private const val HELD_DISPLAY_MS = 1800L

    enum class DisplayMode {
        /** 仅犯规信息（观察期，尚未确定是否点球）。 */
        FOUL_ONLY,
        /** 完整判罚 Banner（含定位球类型与发球方）。 */
        FULL_AWARD,
    }

    var startTimeMs: Long = 0L; private set
    var displayMode: DisplayMode = DisplayMode.FULL_AWARD; private set
    var freeKickType: FreeKickType = FreeKickType.INDIRECT; private set
    var foulReason: FreeKickFoulReason = FreeKickFoulReason.OFFSIDE; private set
    var foulingPlayerName: String = ""; private set
    var foulingTeam: TeamSide = TeamSide.A; private set
    var restartTeam: TeamSide = TeamSide.A; private set

    val isActive: Boolean
        get() {
            if (startTimeMs <= 0) return false
            if (MatchStartClient.isPenaltyKick) return false
            if (System.currentTimeMillis() - startTimeMs < DURATION_MS) return true
            return when (displayMode) {
                DisplayMode.FOUL_ONLY -> MatchStartClient.penaltyFoulGoalWatchActive
                DisplayMode.FULL_AWARD -> isPenaltyBallResetPending()
            }
        }

    val elapsedMs: Long get() = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0L

    val displayElapsedMs: Long
        get() {
            val raw = elapsedMs
            if (raw < DURATION_MS) return raw
            val holdVisible = when (displayMode) {
                DisplayMode.FOUL_ONLY -> MatchStartClient.penaltyFoulGoalWatchActive
                DisplayMode.FULL_AWARD -> isPenaltyBallResetPending()
            }
            if (holdVisible) return HELD_DISPLAY_MS
            return raw
        }

    fun show(
        type: FreeKickType,
        reason: FreeKickFoulReason,
        playerName: String,
        foulingT: TeamSide,
        restartT: TeamSide,
    ) {
        displayMode = DisplayMode.FULL_AWARD
        freeKickType = type
        foulReason = reason
        foulingPlayerName = playerName
        foulingTeam = foulingT
        restartTeam = restartT
        startTimeMs = System.currentTimeMillis()
    }

    /** 禁区内滑铲：吹哨后仅显示犯规，待球出结果后再显示点球判罚。 */
    fun showFoulOnly(
        reason: FreeKickFoulReason,
        playerName: String,
        foulingT: TeamSide,
    ) {
        displayMode = DisplayMode.FOUL_ONLY
        foulReason = reason
        foulingPlayerName = playerName
        foulingTeam = foulingT
        startTimeMs = System.currentTimeMillis()
    }

    /** 观察期结束、确认判罚点球后切换为完整 Banner。 */
    fun confirmPenaltyAward(restartT: TeamSide) {
        displayMode = DisplayMode.FULL_AWARD
        freeKickType = FreeKickType.PENALTY
        restartTeam = restartT
        refreshDisplay()
    }

    fun refreshDisplay() {
        if (startTimeMs > 0) startTimeMs = System.currentTimeMillis()
    }

    fun clear() {
        startTimeMs = 0L
        displayMode = DisplayMode.FULL_AWARD
    }

    private fun isPenaltyBallResetPending(): Boolean =
        MatchStartClient.ballResetPending &&
            MatchStartClient.pendingSetPieceKind == SetPieceKind.PENALTY_KICK &&
            freeKickType == FreeKickType.PENALTY
}
