package net.astrorbits.football.client.match

import net.astrorbits.football.match.KickoffLock
import net.astrorbits.football.match.MatchKickoffTiming
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.TeamSide

object MatchStartClient {
    private const val HUD_DURATION_MS = 6000L
    private const val LOCK_DURATION_MS = MatchKickoffTiming.MATCH_START_LOCK_MS
    private const val POST_GOAL_LOCK_MS = MatchKickoffTiming.POST_GOAL_LOCK_MS
    private const val GOAL_LINE_OUT_LOCK_MS = MatchKickoffTiming.GOAL_LINE_OUT_LOCK_MS
    private const val COUNTDOWN_SECONDS = 20
    private const val GOAL_LINE_OUT_COUNTDOWN = 10
    private const val GRACE_MS = MatchKickoffTiming.LATE_KICKOFF_WARN_MS

    var startTimeMs: Long = 0L; private set
    var playerTeam: TeamSide = TeamSide.A; private set
    var isGk: Boolean = false; private set
    var kickoffTeam: TeamSide = TeamSide.A; private set
    var teamAName: String = ""; private set
    var teamBName: String = ""; private set
    var kickoffTouched: Boolean = false; private set
    var isPostGoal: Boolean = false; private set
    var isGoalLineOut: Boolean = false; private set

    private var kickoffStartMs: Long = 0L
    private var lastStoppageTickMs: Long = 0L

    private val lockDurationMs: Long get() = when {
        isGoalLineOut -> GOAL_LINE_OUT_LOCK_MS
        isPostGoal -> POST_GOAL_LOCK_MS
        else -> LOCK_DURATION_MS
    }
    private val allowedMs: Long get() = lockDurationMs + GRACE_MS

    val isHudActive: Boolean
        get() = !isPostGoal && !isGoalLineOut && startTimeMs > 0 && elapsedMs < HUD_DURATION_MS

    val isLocked: Boolean
        get() = KickoffLock.isPlayerLocked(
            postGoalResetPending = MatchState.postGoalResetPending,
            kickoffPhaseActive = startTimeMs > 0L && !kickoffTouched,
            playerTeam = playerTeam,
            kickoffTeam = kickoffTeam,
            kickoffElapsedMs = elapsedMs,
            kickoffLockMs = lockDurationMs,
        )

    val isChoosing: Boolean
        get() = !isPostGoal && !isGoalLineOut && startTimeMs > 0 && elapsedMs < 3000L

    val countdownSeconds: Int
        get() {
            val remain = (lockDurationMs - elapsedMs + 999L) / 1000L
            val cap = if (isGoalLineOut) GOAL_LINE_OUT_COUNTDOWN.toLong() else COUNTDOWN_SECONDS.toLong()
            return remain.coerceIn(0L, cap).toInt()
        }

    /** 服务端逐玩家计算后下发，直接决定客户端锁定逻辑，不依赖本地 playerTeam */
    var isKickoffTeam: Boolean = false; private set

    fun startMatch(team: TeamSide, gk: Boolean, kickoff: TeamSide, nameA: String, nameB: String) {
        playerTeam = team; isGk = gk; kickoffTeam = kickoff
        isKickoffTeam = team == kickoff
        teamAName = nameA; teamBName = nameB
        kickoffTouched = false; isPostGoal = false; isGoalLineOut = false
        startTimeMs = System.currentTimeMillis()
        kickoffStartMs = startTimeMs; lastStoppageTickMs = 0L
    }

    fun startPostGoalKickoff(kickoff: TeamSide, isKickoffTeam: Boolean) {
        this.isKickoffTeam = isKickoffTeam
        kickoffTeam = kickoff
        kickoffTouched = false; isPostGoal = true; isGoalLineOut = false
        startTimeMs = System.currentTimeMillis()
        kickoffStartMs = startTimeMs; lastStoppageTickMs = 0L
    }

    fun startGoalLineOutKickoff(kickoff: TeamSide, isKickoffTeam: Boolean) {
        this.isKickoffTeam = isKickoffTeam
        kickoffTeam = kickoff
        kickoffTouched = false; isPostGoal = false; isGoalLineOut = true
        startTimeMs = System.currentTimeMillis()
        kickoffStartMs = startTimeMs; lastStoppageTickMs = 0L
    }

    /** 半场开球 HUD */
    var halfKickoffPhaseKey: String = ""; private set
    var halfKickoffActive: Boolean = false; private set
    var halfKickoffStartMs: Long = 0L; private set

    val isHalfKickoffHudActive: Boolean
        get() = halfKickoffActive && (System.currentTimeMillis() - halfKickoffStartMs) < 4000L

    fun startHalfKickoff(kickoff: TeamSide, isKickoffTeam: Boolean, phaseKey: String, nameA: String, nameB: String) {
        this.isKickoffTeam = isKickoffTeam
        kickoffTeam = kickoff; teamAName = nameA; teamBName = nameB
        kickoffTouched = false; isPostGoal = true; isGoalLineOut = false
        halfKickoffPhaseKey = phaseKey; halfKickoffActive = true
        halfKickoffStartMs = System.currentTimeMillis()
        startTimeMs = halfKickoffStartMs; kickoffStartMs = startTimeMs; lastStoppageTickMs = 0L
    }

    /** 仅预览半场 Banner，不启动开球锁定流程。 */
    fun previewHalfKickoffHud(phaseKey: String, kickoff: TeamSide, nameA: String, nameB: String) {
        kickoffTeam = kickoff
        teamAName = nameA
        teamBName = nameB
        halfKickoffPhaseKey = phaseKey
        halfKickoffActive = true
        halfKickoffStartMs = System.currentTimeMillis()
    }

    fun onBallTouched() {
        if (kickoffTouched) return
        kickoffTouched = true
        isGoalLineOut = false
        lastStoppageTickMs = 0L
    }

    /** 半场切换时调用，终止当前未完成的开球计时 */
    fun cancelKickoff() {
        kickoffTouched = true
        kickoffStartMs = 0L
        lastStoppageTickMs = 0L
    }

    val elapsedMs: Long get() = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0L
    fun reset() {
        startTimeMs = 0L
        kickoffTouched = false
        isPostGoal = false
        isGoalLineOut = false
        halfKickoffActive = false
        kickoffStartMs = 0L
        lastStoppageTickMs = 0L
    }
}
