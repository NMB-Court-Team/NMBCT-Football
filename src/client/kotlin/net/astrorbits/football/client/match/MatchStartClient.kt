package net.astrorbits.football.client.match

import net.astrorbits.football.client.FootballOperabilityClient
import net.astrorbits.football.match.KickoffLock
import net.astrorbits.football.match.MatchKickoffTiming
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.PenaltyShootoutTiming
import net.astrorbits.football.match.SetPieceKind
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
    var isPenaltyKick: Boolean = false; private set

    /** 球延迟复位期间（服务端 [MatchState.postGoalResetPending] 镜像）。 */
    var ballResetPending: Boolean = false; private set
    /** 禁区内滑铲犯规：吹哨后全员禁操作，等待球是否进门。 */
    var penaltyFoulGoalWatchActive: Boolean = false; private set
    var pendingRestartTeam: TeamSide? = null; private set
    var pendingSetPieceKind: SetPieceKind? = null; private set

    private var kickoffStartMs: Long = 0L
    private var lastStoppageTickMs: Long = 0L

    private val lockDurationMs: Long get() = when {
        isPenaltyKick -> PenaltyShootoutTiming.KICK_INTRO_LOCK_MS
        isGoalLineOut -> GOAL_LINE_OUT_LOCK_MS
        isPostGoal -> POST_GOAL_LOCK_MS
        else -> LOCK_DURATION_MS
    }
    private val allowedMs: Long get() = lockDurationMs + GRACE_MS

    val isHudActive: Boolean
        get() = !isPostGoal && !isGoalLineOut && !isPenaltyKick &&
            startTimeMs > 0 && elapsedMs < HUD_DURATION_MS

    val isLocked: Boolean
        get() {
            val client = net.minecraft.client.Minecraft.getInstance().player
            if (client?.isSpectator == true) {
                return startTimeMs > 0L && !kickoffTouched
            }
            val effectiveKickoffTeam = pendingRestartTeam ?: kickoffTeam
            val effectivePlayerTeam =
                client?.let { FootballOperabilityClient.resolveLocalPlayerTeam(it) } ?: playerTeam
            return KickoffLock.isPlayerLocked(
                kickoffPhaseActive = startTimeMs > 0L && !kickoffTouched,
                playerTeam = effectivePlayerTeam,
                kickoffTeam = effectiveKickoffTeam,
                kickoffElapsedMs = elapsedMs,
                kickoffLockMs = lockDurationMs,
            )
        }

    val isChoosing: Boolean
        get() = !isPostGoal && !isGoalLineOut && !isPenaltyKick &&
            startTimeMs > 0 && elapsedMs < 3000L

    val countdownSeconds: Int
        get() {
            val remain = (lockDurationMs - elapsedMs + 999L) / 1000L
            val cap = when {
                isPenaltyKick -> (PenaltyShootoutTiming.KICK_INTRO_LOCK_MS / 1000L)
                isGoalLineOut -> GOAL_LINE_OUT_COUNTDOWN.toLong()
                else -> COUNTDOWN_SECONDS.toLong()
            }
            return remain.coerceIn(0L, cap).toInt()
        }

    /** 服务端逐玩家计算后下发，直接决定客户端锁定逻辑，不依赖本地 playerTeam */
    var isKickoffTeam: Boolean = false; private set

    /** 仅同步本队身份（点球大战等不触发开球锁的场景）。 */
    fun assignTeam(team: TeamSide, nameA: String, nameB: String) {
        playerTeam = team
        teamAName = nameA
        teamBName = nameB
    }

    fun startMatch(team: TeamSide, gk: Boolean, kickoff: TeamSide, nameA: String, nameB: String) {
        clearBallResetPending()
        playerTeam = team; isGk = gk; kickoffTeam = kickoff
        isKickoffTeam = team == kickoff
        teamAName = nameA; teamBName = nameB
        kickoffTouched = false; isPostGoal = false; isGoalLineOut = false; isPenaltyKick = false
        startTimeMs = System.currentTimeMillis()
        kickoffStartMs = startTimeMs; lastStoppageTickMs = 0L
    }

    fun startPenaltyKick(kickerTeam: TeamSide) {
        clearBallResetPending()
        kickoffTeam = kickerTeam
        isKickoffTeam = playerTeam == kickerTeam
        kickoffTouched = false
        isPostGoal = false
        isGoalLineOut = false
        isPenaltyKick = true
        startTimeMs = System.currentTimeMillis()
        kickoffStartMs = startTimeMs
        lastStoppageTickMs = 0L
    }

    fun beginPenaltyFoulGoalWatch() {
        penaltyFoulGoalWatchActive = true
    }

    fun clearPenaltyFoulGoalWatch() {
        penaltyFoulGoalWatchActive = false
    }

    fun beginBallResetPending(restartTeam: TeamSide, setPieceKind: SetPieceKind) {
        ballResetPending = true
        pendingRestartTeam = restartTeam
        pendingSetPieceKind = setPieceKind
        kickoffTeam = restartTeam
        isKickoffTeam = playerTeam == restartTeam
        startTimeMs = 0L
        kickoffStartMs = 0L
        kickoffTouched = true
    }

    fun clearBallResetPending() {
        ballResetPending = false
        pendingRestartTeam = null
        pendingSetPieceKind = null
    }

    fun startPostGoalKickoff(kickoff: TeamSide, isKickoffTeam: Boolean) {
        clearBallResetPending()
        this.isKickoffTeam = isKickoffTeam
        kickoffTeam = kickoff
        kickoffTouched = false; isPostGoal = true; isGoalLineOut = false; isPenaltyKick = false
        startTimeMs = System.currentTimeMillis()
        kickoffStartMs = startTimeMs; lastStoppageTickMs = 0L
    }

    fun startGoalLineOutKickoff(kickoff: TeamSide, isKickoffTeam: Boolean) {
        clearBallResetPending()
        this.isKickoffTeam = isKickoffTeam
        kickoffTeam = kickoff
        kickoffTouched = false; isPostGoal = false; isGoalLineOut = true; isPenaltyKick = false
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
        clearBallResetPending()
        this.isKickoffTeam = isKickoffTeam
        kickoffTeam = kickoff; teamAName = nameA; teamBName = nameB
        kickoffTouched = false; isPostGoal = true; isGoalLineOut = false; isPenaltyKick = false
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
        isPenaltyKick = false
        lastStoppageTickMs = 0L
    }

    /** 半场切换时调用，终止当前未完成的开球计时 */
    fun cancelKickoff() {
        kickoffTouched = true
        kickoffStartMs = 0L
        lastStoppageTickMs = 0L
    }

    /**
     * 进入新半场时清理上一半场的开球状态。
     * 若 [HalfKickoffS2CPayload] 已先到达（[halfKickoffActive]），不得将 [kickoffTouched] 置 true，
     * 否则会抑制半场开球粒子与锁定 HUD。
     */
    fun prepareForHalfTransition() {
        if (!halfKickoffActive) {
            startTimeMs = 0L
            kickoffTouched = true
            isPostGoal = false
            isGoalLineOut = false
            isPenaltyKick = false
        }
        kickoffStartMs = 0L
        lastStoppageTickMs = 0L
    }

    val elapsedMs: Long get() = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0L
    fun reset() {
        clearBallResetPending()
        clearPenaltyFoulGoalWatch()
        startTimeMs = 0L
        kickoffTouched = false
        isPostGoal = false
        isGoalLineOut = false
        isPenaltyKick = false
        halfKickoffActive = false
        kickoffStartMs = 0L
        lastStoppageTickMs = 0L
    }
}
