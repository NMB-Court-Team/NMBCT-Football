package net.astrorbits.football.client.match

import net.astrorbits.football.match.TeamSide

object MatchStartClient {
    /** 中央大字 HUD 持续时间 */
    private const val HUD_DURATION_MS = 6000L
    /** 踢球锁定总时长（3s 选择 + 20s 倒计时） */
    private const val LOCK_DURATION_MS = 23000L
    /** 进球后开球锁定总时长（20s 倒计时，无选择阶段） */
    private const val POST_GOAL_LOCK_MS = 20000L
    /** 倒计时起始秒数 */
    private const val COUNTDOWN_SECONDS = 20

    var startTimeMs: Long = 0L; private set
    var playerTeam: TeamSide = TeamSide.A; private set
    var isGk: Boolean = false; private set
    var kickoffTeam: TeamSide = TeamSide.A; private set
    var teamAName: String = ""; private set
    var teamBName: String = ""; private set
    var kickoffTouched: Boolean = false; private set
    /** 是否为进球后开球（无中央 HUD，无选择阶段） */
    var isPostGoal: Boolean = false; private set

    private val lockDurationMs: Long
        get() = if (isPostGoal) POST_GOAL_LOCK_MS else LOCK_DURATION_MS

    val isHudActive: Boolean
        get() = !isPostGoal && startTimeMs > 0 && elapsedMs < HUD_DURATION_MS

    val isLocked: Boolean
        get() {
            if (startTimeMs == 0L) return false
            if (elapsedMs < lockDurationMs) return true
            if (!isKickoffTeam && !kickoffTouched) return true
            return false
        }

    val isChoosing: Boolean
        get() = !isPostGoal && startTimeMs > 0 && elapsedMs < 3000L

    val countdownSeconds: Int
        get() {
            val remain = (lockDurationMs - elapsedMs) / 1000L
            return remain.coerceIn(0L, COUNTDOWN_SECONDS.toLong()).toInt()
        }

    val isKickoffTeam: Boolean
        get() = playerTeam == kickoffTeam

    /** 开场开球（随机发球方 + 中央 HUD + 3s 选择） */
    fun startMatch(team: TeamSide, gk: Boolean, kickoff: TeamSide, nameA: String, nameB: String) {
        playerTeam = team
        isGk = gk
        kickoffTeam = kickoff
        teamAName = nameA
        teamBName = nameB
        kickoffTouched = false
        isPostGoal = false
        startTimeMs = System.currentTimeMillis()
    }

    /** 进球后开球（失分方发球，仅底部警告，20s 倒数） */
    fun startPostGoalKickoff(kickoff: TeamSide) {
        kickoffTeam = kickoff
        kickoffTouched = false
        isPostGoal = true
        startTimeMs = System.currentTimeMillis()
    }

    fun onBallTouched() { kickoffTouched = true }

    val elapsedMs: Long get() = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0L
    fun reset() { startTimeMs = 0L; kickoffTouched = false; isPostGoal = false }
}
