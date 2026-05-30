package net.astrorbits.football.client.match

import net.astrorbits.football.match.TeamSide

object MatchStartClient {
    /** 中央大字 HUD 持续时间 */
    private const val HUD_DURATION_MS = 6000L
    /** 踢球锁定总时长（3s 选择 + 20s 倒计时） */
    private const val LOCK_DURATION_MS = 23000L
    /** 倒计时起始秒数 */
    private const val COUNTDOWN_SECONDS = 20

    var startTimeMs: Long = 0L; private set
    var playerTeam: TeamSide = TeamSide.A; private set
    var isGk: Boolean = false; private set
    var kickoffTeam: TeamSide = TeamSide.A; private set
    var teamAName: String = ""; private set
    var teamBName: String = ""; private set
    var kickoffTouched: Boolean = false; private set

    val isHudActive: Boolean
        get() = startTimeMs > 0 && elapsedMs < HUD_DURATION_MS

    /** 踢球操作是否被锁定（计时空锁 + 非发球方触球锁） */
    val isLocked: Boolean
        get() {
            if (startTimeMs == 0L) return false
            if (elapsedMs < LOCK_DURATION_MS) return true
            if (!isKickoffTeam && !kickoffTouched) return true
            return false
        }

    /** 是否在选择发球方阶段（前 3 秒） */
    val isChoosing: Boolean
        get() = startTimeMs > 0 && elapsedMs < 3000L

    /** 倒计时剩余秒数（选择阶段结束后开始倒数） */
    val countdownSeconds: Int
        get() {
            val remain = (LOCK_DURATION_MS - elapsedMs) / 1000L
            return remain.coerceIn(0L, COUNTDOWN_SECONDS.toLong()).toInt()
        }

    /** 本队是否为发球方 */
    val isKickoffTeam: Boolean
        get() = playerTeam == kickoffTeam

    fun start(team: TeamSide, gk: Boolean, kickoff: TeamSide, nameA: String, nameB: String) {
        playerTeam = team
        isGk = gk
        kickoffTeam = kickoff
        teamAName = nameA
        teamBName = nameB
        kickoffTouched = false
        startTimeMs = System.currentTimeMillis()
    }

    fun onBallTouched() { kickoffTouched = true }

    val elapsedMs: Long get() = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0L
    fun reset() { startTimeMs = 0L; kickoffTouched = false }
}
