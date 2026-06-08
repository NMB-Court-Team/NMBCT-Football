package net.astrorbits.football.match

/**
 * 开球/定位球发球锁定期间「能否操作足球、能否碰球」的判定。
 * 球延迟复位宽限（[MatchState.postGoalResetPending]）不触发锁定，仅禁用判例检测。
 * 客户端 HUD/按键与服务端操作包/碰撞共用。
 */
object KickoffLock {
    fun isKickoffPhaseActive(kickoffTeam: TeamSide?, kickoffTouched: Boolean, kickoffTimerStartMs: Long): Boolean =
        kickoffTeam != null && !kickoffTouched && kickoffTimerStartMs != 0L

    /**
     * @param kickoffPhaseActive 开球阶段已开始且尚未触球解锁
     * @param kickoffElapsedMs 本阶段已过去时间；[kickoffLockMs] 为 HUD 倒计时总长（锁定时长）
     */
    fun isPlayerLocked(
        kickoffPhaseActive: Boolean,
        playerTeam: TeamSide?,
        kickoffTeam: TeamSide?,
        kickoffElapsedMs: Long,
        kickoffLockMs: Long,
    ): Boolean {
        if (!kickoffPhaseActive) return false
        if (kickoffElapsedMs < kickoffLockMs) return true
        if (playerTeam == null || kickoffTeam == null) return false
        return playerTeam != kickoffTeam
    }
}
