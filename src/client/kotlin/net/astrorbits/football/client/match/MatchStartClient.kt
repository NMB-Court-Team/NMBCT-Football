package net.astrorbits.football.client.match

import net.astrorbits.football.match.TeamSide

object MatchStartClient {
    private const val HUD_DURATION_MS = 6000L
    private const val LOCK_DURATION_MS = 23000L
    private const val POST_GOAL_LOCK_MS = 20000L
    private const val GOAL_LINE_OUT_LOCK_MS = 10000L
    private const val COUNTDOWN_SECONDS = 20
    private const val GOAL_LINE_OUT_COUNTDOWN = 10
    private const val GRACE_MS = 10000L  // 宽限期 10 秒

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
        get() {
            if (startTimeMs == 0L) return false
            if (elapsedMs < lockDurationMs) return true
            if (!isKickoffTeam && !kickoffTouched) return true
            return false
        }

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

    fun onBallTouched() {
        if (kickoffTouched) return
        kickoffTouched = true
        isGoalLineOut = false
        // 补时上限
        val maxTicks = net.astrorbits.football.match.MatchConfigHolder.current.stoppageTimeMaxMinutes * 60 * 20
        if (net.astrorbits.football.match.MatchState.dynamicStoppageTicks > maxTicks) {
            net.astrorbits.football.match.MatchState.dynamicStoppageTicks = maxTicks
        }
        lastStoppageTickMs = 0L
    }

    /** 每客户端 tick 调用：宽限期过后未触球则持续累积动态补时 */
    fun tickStoppage() {
        if (startTimeMs == 0L || kickoffTouched) return
        val now = System.currentTimeMillis()
        val graceEnd = kickoffStartMs + allowedMs
        if (now <= graceEnd) return
        val maxTicks = net.astrorbits.football.match.MatchConfigHolder.current.stoppageTimeMaxMinutes * 60 * 20
        val state = net.astrorbits.football.match.MatchState
        if (state.dynamicStoppageTicks >= maxTicks) return
        if (lastStoppageTickMs == 0L) lastStoppageTickMs = graceEnd
        val delta = now - lastStoppageTickMs
        if (delta >= 50) {
            val ticks = (delta / 50).toInt().coerceAtMost(maxTicks - state.dynamicStoppageTicks)
            state.dynamicStoppageTicks += ticks
            lastStoppageTickMs += ticks * 50L
        }
    }

    /** 半场切换时调用，终止当前未完成的开球计时 */
    fun cancelKickoff() {
        kickoffTouched = true
        kickoffStartMs = 0L
        lastStoppageTickMs = 0L
    }

    val elapsedMs: Long get() = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0L
    fun reset() { startTimeMs = 0L; kickoffTouched = false; isPostGoal = false; isGoalLineOut = false }
}
