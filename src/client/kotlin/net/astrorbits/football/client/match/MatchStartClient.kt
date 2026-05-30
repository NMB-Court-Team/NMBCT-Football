package net.astrorbits.football.client.match

import net.astrorbits.football.match.TeamSide

object MatchStartClient {
    private const val HUD_DURATION_MS = 6000L
    private const val LOCK_DURATION_MS = 23000L
    private const val POST_GOAL_LOCK_MS = 20000L
    private const val COUNTDOWN_SECONDS = 20
    private const val GRACE_MS = 10000L  // 宽限期 10 秒

    var startTimeMs: Long = 0L; private set
    var playerTeam: TeamSide = TeamSide.A; private set
    var isGk: Boolean = false; private set
    var kickoffTeam: TeamSide = TeamSide.A; private set
    var teamAName: String = ""; private set
    var teamBName: String = ""; private set
    var kickoffTouched: Boolean = false; private set
    var isPostGoal: Boolean = false; private set

    private var kickoffStartMs: Long = 0L
    private var lastStoppageTickMs: Long = 0L

    private val lockDurationMs: Long get() = if (isPostGoal) POST_GOAL_LOCK_MS else LOCK_DURATION_MS
    private val allowedMs: Long get() = lockDurationMs + GRACE_MS

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

    val isKickoffTeam: Boolean get() = playerTeam == kickoffTeam

    fun startMatch(team: TeamSide, gk: Boolean, kickoff: TeamSide, nameA: String, nameB: String) {
        playerTeam = team; isGk = gk; kickoffTeam = kickoff
        teamAName = nameA; teamBName = nameB
        kickoffTouched = false; isPostGoal = false
        startTimeMs = System.currentTimeMillis()
        kickoffStartMs = startTimeMs; lastStoppageTickMs = 0L
    }

    fun startPostGoalKickoff(kickoff: TeamSide) {
        kickoffTeam = kickoff
        kickoffTouched = false; isPostGoal = true
        startTimeMs = System.currentTimeMillis()
        kickoffStartMs = startTimeMs; lastStoppageTickMs = 0L
    }

    fun onBallTouched() {
        if (kickoffTouched) return
        kickoffTouched = true
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

    val elapsedMs: Long get() = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0L
    fun reset() { startTimeMs = 0L; kickoffTouched = false; isPostGoal = false }
}
