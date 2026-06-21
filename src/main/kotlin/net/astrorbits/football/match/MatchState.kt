package net.astrorbits.football.match

import net.astrorbits.football.Football
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.util.GoalkeeperUtil
import net.astrorbits.football.match.MatchState.isRunning
import net.astrorbits.football.match.MatchState.tryNotifyKickoffBallTouched
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.stamina.StaminaState
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.*

object MatchState {
    private val DEFAULT_TEAM_A_NAME = Component.translatable("team_name.nmbct-football.teamA").withStyle(ChatFormatting.RED)
    private val DEFAULT_TEAM_B_NAME = Component.translatable("team_name.nmbct-football.teamB").withStyle(ChatFormatting.BLUE)
    private val SPECTATOR_TEAM_NAME = Component.translatable("team_name.nmbct-football.spec").withStyle(ChatFormatting.GRAY)

    private const val SCOREBOARD_TEAM_A = "football_A"
    private const val SCOREBOARD_TEAM_B = "football_B"
    private val ALL_FOOTBALLS_AABB = AABB(Vec3(-3.0E7, -3.0E7, -3.0E7), Vec3(3.0E7, 3.0E7, 3.0E7))
    private const val SCOREBOARD_TEAM_SPEC = "spec"
    private const val KICKOFF_BODY_CONTACT_RELEASE_DELAY_TICKS = 5L

    var timerTicks = 0
    var stoppageTimerTicks = 0
    var isRunning = true
    var currentPhase: MatchPhase = MatchPhase.PRE_MATCH
    var teamAName: Component = DEFAULT_TEAM_A_NAME
    var teamBName: Component = DEFAULT_TEAM_B_NAME
    var teamAScore = 0
    var teamBScore = 0
    /** 全队罚下判负时的获胜方；正赛结算时优先于比分判定胜负。 */
    var forfeitWinner: TeamSide? = null
    val teamAPlayers: MutableSet<UUID> = mutableSetOf()
    val teamBPlayers: MutableSet<UUID> = mutableSetOf()
    val spectatorPlayers: MutableSet<UUID> = mutableSetOf()
    private val spectatorPreviousGameModes: MutableMap<UUID, GameType> = mutableMapOf()
    var kickoffTeam: TeamSide? = null
    var kickoffTouched: Boolean = false
    /** 发球方合法触球后仍抑制身体推球，直至该 tick（含）。 */
    private var kickoffBodyContactReleaseUntilTick: Long = -1L
    private var kickoffTimerStartMs: Long = 0L
    private var kickoffLockMs: Long = 0L
    private var kickoffWhistleContext: KickoffWhistleContext? = null
    private var kickoffCountdownEndHandled: Boolean = false
    private var kickoffWhistle3Played: Boolean = false
    private var kickoffWhistle5Played: Boolean = false
    private var lastDynamicStoppageAccumMs: Long = 0L
    /** 本半场动态累积的补时时长（tick），由服务端根据开球拖延计算。 */
    var dynamicStoppageTicks: Int = 0
    /** 半场开球是否已广播（防重复） */
    var halfKickoffBroadcasted: Boolean = false
    /** 上一个半场的发球方，用于下一半场交替 */
    var lastHalfKickoffTeam: TeamSide? = null
    /** 进球或出界后、等待延迟复位期间为 true，防止重复判例。 */
    var postGoalResetPending: Boolean = false
    /**
     * 门柱外侧穿越门线后的短暂确认：若球随后弹入球门则改判进球，避免打框进门被判出界。
     */
    var pendingGoalLineOut: PendingGoalLineOut? = null

    data class PendingGoalLineOut(
        val goal: GoalConfig,
        val ballPos: Vec3,
        val restartTeam: TeamSide,
        val outType: GoalLineOutType,
        val defendingTeam: TeamSide,
        val attackingTeam: TeamSide,
        var ticksRemaining: Int = GOAL_LINE_OUT_CONFIRM_TICKS,
    )

    private const val GOAL_LINE_OUT_CONFIRM_TICKS = 10

    fun clearPendingGoalLineOut() {
        pendingGoalLineOut = null
    }
    /** 掷界外球（出边线）开球后：须先由其他球员获得进球归属，否则直接进门无效。 */
    var directGoalRestricted: Boolean = false
    private var directGoalInitialAttribution: UUID? = null

    data class OffsideSnapshot(
        val passerUuid: UUID,
        val attackingTeam: TeamSide,
        val offsidePlayers: Set<UUID>,
        val gameTime: Long,
    )

    var pendingOffsideSnapshot: OffsideSnapshot? = null

    fun clearPendingOffsideSnapshot() {
        pendingOffsideSnapshot = null
    }

    fun getTeamName(team: TeamSide): Component = when (team) {
        TeamSide.A -> teamAName.copy().withStyle(ChatFormatting.RED)
        TeamSide.B -> teamBName.copy().withStyle(ChatFormatting.AQUA)
    }

    fun getPlayerTeam(uuid: UUID): TeamSide? = when {
        teamAPlayers.contains(uuid) -> TeamSide.A
        teamBPlayers.contains(uuid) -> TeamSide.B
        else -> null
    }

    /** 是否处于正式比赛阶段（非 [MatchPhase.PRE_MATCH]、非 [MatchPhase.PRE_MATCH_PREP]、非 [MatchPhase.FINISHED]；含常规半场、补时、加时、点球大战）。 */
    fun isDuringMatch(): Boolean =
        currentPhase != MatchPhase.PRE_MATCH
            && currentPhase != MatchPhase.PRE_MATCH_PREP
            && currentPhase != MatchPhase.FINISHED

    /** 赛前准备阶段：可调整守门员与按键，但不判进球/出界。 */
    fun isPreMatchPreparationPhase(): Boolean = currentPhase == MatchPhase.PRE_MATCH_PREP

    /** 正式比赛或赛前准备：守门员身份与相关输入生效。 */
    fun allowsActiveGoalkeeperRole(): Boolean = isDuringMatch() || isPreMatchPreparationPhase()

    /** 比赛计时是否暂停（含赛前准备阶段，即 [isRunning] 为 false 且仍在可计时阶段）。点球大战不计时，但不视为暂停。 */
    fun isMatchTimerPaused(): Boolean =
        (isDuringMatch() || isPreMatchPreparationPhase())
            && !isRunning
            && currentPhase != MatchPhase.PENALTIES

    fun addPlayer(team: TeamSide, uuid: UUID) {
        when (team) {
            TeamSide.A -> teamAPlayers.add(uuid)
            TeamSide.B -> teamBPlayers.add(uuid)
        }
    }

    fun addSpectator(player: ServerPlayer, server: MinecraftServer) {
        spectatorPlayers.add(player.uuid)
        syncPlayerScoreboard(player.uuid, null, server, spectator = true)
        if (currentPhase != MatchPhase.PRE_MATCH && currentPhase != MatchPhase.FINISHED) {
            activateSpectator(player)
        }
    }

    fun removePlayer(uuid: UUID, server: MinecraftServer? = null): Boolean {
        val removed = teamAPlayers.remove(uuid) or teamBPlayers.remove(uuid) or spectatorPlayers.remove(uuid)
        if (removed && server != null) {
            restoreSpectator(server.playerList.getPlayer(uuid))
        }
        return removed
    }

    fun syncPlayerScoreboard(uuid: UUID, team: TeamSide?, server: MinecraftServer, spectator: Boolean = false) {
        val player = server.playerList.getPlayer(uuid) ?: return
        val playerName = player.gameProfile.name
        val scoreboard = server.scoreboard

        for (teamKey in listOf(SCOREBOARD_TEAM_A, SCOREBOARD_TEAM_B, SCOREBOARD_TEAM_SPEC)) {
            val sbTeam = scoreboard.getPlayerTeam(teamKey) ?: continue
            if (sbTeam.players.contains(playerName)) {
                scoreboard.removePlayerFromTeam(playerName, sbTeam)
            }
        }

        if (spectator) {
            val sbTeam = scoreboard.getPlayerTeam(SCOREBOARD_TEAM_SPEC) ?: run {
                val t = scoreboard.addPlayerTeam(SCOREBOARD_TEAM_SPEC)
                t.setColor(ChatFormatting.GRAY)
                t.displayName = SPECTATOR_TEAM_NAME
                t
            }
            scoreboard.addPlayerToTeam(playerName, sbTeam)
        } else if (team != null) {
            val teamKey = when (team) {
                TeamSide.A -> SCOREBOARD_TEAM_A
                TeamSide.B -> SCOREBOARD_TEAM_B
            }
            val color = when (team) {
                TeamSide.A -> ChatFormatting.RED
                TeamSide.B -> ChatFormatting.AQUA
            }
            val sbTeam = scoreboard.getPlayerTeam(teamKey) ?: run {
                val t = scoreboard.addPlayerTeam(teamKey)
                t.setColor(color)
                t.displayName = getTeamName(team)
                t
            }
            scoreboard.addPlayerToTeam(playerName, sbTeam)
        }
    }

    /**
     * 大厅数据包和原版 scoreboard 可能在服务端重载后仍保留队伍显示，
     * 但内存名单会丢失；开赛前用在线玩家的 scoreboard 队伍补回名单。
     */
    fun syncOnlineRostersFromScoreboard(server: MinecraftServer) {
        for (player in server.playerList.players) {
            val uuid = player.uuid
            when (server.scoreboard.getPlayersTeam(player.gameProfile.name)?.name) {
                SCOREBOARD_TEAM_A -> {
                    teamBPlayers.remove(uuid)
                    spectatorPlayers.remove(uuid)
                    teamAPlayers.add(uuid)
                }
                SCOREBOARD_TEAM_B -> {
                    teamAPlayers.remove(uuid)
                    spectatorPlayers.remove(uuid)
                    teamBPlayers.add(uuid)
                }
                SCOREBOARD_TEAM_SPEC -> {
                    teamAPlayers.remove(uuid)
                    teamBPlayers.remove(uuid)
                    spectatorPlayers.add(uuid)
                }
            }
        }
    }

    fun clearScoreboardTeams(server: MinecraftServer) {
        val scoreboard = server.scoreboard
        for (teamKey in listOf(SCOREBOARD_TEAM_A, SCOREBOARD_TEAM_B, SCOREBOARD_TEAM_SPEC)) {
            scoreboard.getPlayerTeam(teamKey)?.let { team ->
                val players = team.players.toList()
                for (playerName in players) {
                    scoreboard.removePlayerFromTeam(playerName, team)
                }
            }
        }
    }

    fun activateSpectators(server: MinecraftServer) {
        for (uuid in spectatorPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            activateSpectator(player)
        }
    }

    fun restoreSpectators(server: MinecraftServer) {
        for (uuid in spectatorPreviousGameModes.keys.toList()) {
            restoreSpectator(server.playerList.getPlayer(uuid))
        }
    }

    private fun activateSpectator(player: ServerPlayer) {
        spectatorPreviousGameModes.putIfAbsent(
            player.uuid,
            player.gameMode.gameModeForPlayer,
        )
        player.setGameMode(GameType.SPECTATOR)
        val center = MatchConfigHolder.current.kickOff
        player.teleportTo(
            player.level(),
            center.x,
            center.y,
            center.z,
            HashSet(),
            player.yRot,
            player.xRot,
            false,
        )
    }

    private fun restoreSpectator(player: ServerPlayer?) {
        if (player == null) return
        val previous = spectatorPreviousGameModes.remove(player.uuid) ?: return
        player.setGameMode(previous)
    }

    fun reset() {
        timerTicks = 0
        stoppageTimerTicks = 0
        currentPhase = MatchPhase.PRE_MATCH
        isRunning = false
        teamAScore = 0
        teamBScore = 0
        forfeitWinner = null
        kickoffTeam = null
        kickoffTouched = false
        kickoffBodyContactReleaseUntilTick = -1L
        clearKickoffWhistleTimers()
        dynamicStoppageTicks = 0
        lastDynamicStoppageAccumMs = 0L
        halfKickoffBroadcasted = false
        lastHalfKickoffTeam = null
        postGoalResetPending = false
        PenaltyFoulGoalWatchState.clear()
        clearPendingGoalLineOut()
        clearDirectGoalRestriction()
        clearPendingOffsideSnapshot()
        PlayerRoleState.reset()
        PenaltyShootoutState.clear()
        MatchPenaltyKickState.clear()
        SetPieceState.clear()
        SecondTouchTracker.clear()
        MatchSendOffState.clear()
    }

    fun kickoffWhistleContext(): KickoffWhistleContext? = kickoffWhistleContext

    fun forceKickoffBallTouched(resumeGameTick: Long) {
        if (kickoffTouched) return
        kickoffTouched = true
        scheduleKickoffBodyContactRelease(resumeGameTick)
        clearKickoffWhistleTimers()
    }

    fun beginThrowInDirectGoalRestriction() {
        directGoalRestricted = true
        directGoalInitialAttribution = null
    }

    fun clearDirectGoalRestriction() {
        directGoalRestricted = false
        directGoalInitialAttribution = null
    }

    /** 新足球放置后重置“首触归属”记录，限制规则仍有效。 */
    fun onRestrictedRestartBallPlaced() {
        if (directGoalRestricted) {
            directGoalInitialAttribution = null
        }
    }

    /** 进球归属球员变更时更新直接进球限制状态。 */
    fun onGoalAttributionChanged(newAttribution: UUID) {
        if (!directGoalRestricted) return
        val initial = directGoalInitialAttribution
        if (initial == null) {
            directGoalInitialAttribution = newAttribution
            return
        }
        if (newAttribution != initial) {
            clearDirectGoalRestriction()
        }
    }

    fun isDirectGoalInvalid(goalAttributionPlayer: UUID?, lastPhysicalTouch: UUID?): Boolean {
        if (!directGoalRestricted) return false
        val current = goalAttributionPlayer ?: lastPhysicalTouch ?: return true
        val initial = directGoalInitialAttribution ?: return true
        return current == initial
    }

    fun togglePause() {
        isRunning = !isRunning
    }

    /** 进球处理：scoringTeam 为得分队伍 */
    fun onGoal(scoringTeam: TeamSide) {
        when (scoringTeam) {
            TeamSide.A -> teamAScore++
            TeamSide.B -> teamBScore++
        }
    }

    fun isKickoffInteractionLocked(player: ServerPlayer): Boolean =
        isKickoffInteractionLocked(player, action = null)

    fun isKickoffInteractionLocked(player: ServerPlayer, action: net.astrorbits.football.network.FootballActionType?): Boolean {
        if (!MatchParticipation.isParticipating(player)) {
            return true
        }
        if (PenaltyFoulGoalWatchState.isActive()) {
            return true
        }
        if (postGoalResetPending) {
            return false
        }
        if (SetPieceRestrictionCoordinator.allowsGoalKickCatch(player) ||
            SetPieceRestrictionCoordinator.allowsGoalKickDrop(player)
        ) {
            return false
        }
        if (SetPieceRestrictionCoordinator.allowsGoalKickPlacedKick(player) &&
            allowsGoalKickPlacedKickDuringKickoffLock(action)
        ) {
            return false
        }
        if (SetPieceRestrictionCoordinator.isFootballOperationBlocked(player, action)) {
            return true
        }
        if (SetPieceRestrictionCoordinator.isPassOnlyViolation(player, action)) {
            return true
        }
        if (SetPieceRestrictionCoordinator.isPlayerBallMovementForbidden(player)) {
            return true
        }
        if (ThrowInSetPieceFlow.isMovementFrozen(player) && !ThrowInSetPieceFlow.allowsThrowAction(player, action)) {
            return true
        }
        if (PenaltyShootoutState.deniesPenaltyKickerAction(player, action)) {
            return true
        }
        if (MatchPenaltyKickState.deniesPenaltyKickerAction(player, action)) {
            return true
        }
        if (PenaltyShootoutState.isActive() && PenaltyShootoutState.isPenaltyFootballInteractionAllowed(player)) {
            return false
        }
        if (MatchPenaltyKickState.isActive() && MatchPenaltyKickState.isFootballInteractionAllowed(player)) {
            return false
        }
        if (PenaltyShootoutState.isPenaltyMovementRestricted(player)) {
            return true
        }
        if (MatchPenaltyKickState.isMovementRestricted(player)) {
            return true
        }
        if (PenaltyShootoutState.allowsPenaltyKickerAction(player, action) ||
            PenaltyShootoutState.allowsPenaltyGoalkeeperAction(player, action)
        ) {
            return false
        }
        if (MatchPenaltyKickState.allowsPenaltyKickerAction(player, action) ||
            MatchPenaltyKickState.allowsPenaltyGoalkeeperAction(player, action)
        ) {
            return false
        }
        if (currentPhase == MatchPhase.PENALTIES && !PenaltyShootoutState.isPenaltyFootballInteractionAllowed(player)) {
            if (!PenaltyShootoutState.allowsPenaltyGoalkeeperAction(player, action)) {
                return true
            }
        }
        if (MatchPenaltyKickState.isActive() && !MatchPenaltyKickState.isFootballInteractionAllowed(player)) {
            if (!MatchPenaltyKickState.allowsPenaltyGoalkeeperAction(player, action)) {
                return true
            }
        }
        if (SetPieceRestrictionCoordinator.allowsFreeKickDefendingGoalkeeperHoldAction(player, action)) {
            return false
        }
        if (GoalKickSetPieceFlow.isAwaitingPenaltyAreaExit()) {
            return false
        }
        val activeKickoffTeam = activeRestartTeam()
        val phaseActive = KickoffLock.isKickoffPhaseActive(activeKickoffTeam, kickoffTouched, kickoffTimerStartMs)
        val elapsed = if (phaseActive) System.currentTimeMillis() - kickoffTimerStartMs else 0L
        return KickoffLock.isPlayerLocked(
            kickoffPhaseActive = phaseActive,
            playerTeam = MatchParticipation.participatingTeam(player),
            kickoffTeam = activeKickoffTeam,
            kickoffElapsedMs = elapsed,
            kickoffLockMs = kickoffLockMs,
        )
    }

    private fun activeRestartTeam(): TeamSide? =
        SetPieceState.active?.restartTeam ?: kickoffTeam

    private fun allowsGoalKickPlacedKickDuringKickoffLock(
        action: net.astrorbits.football.network.FootballActionType?,
    ): Boolean = when (action) {
        null,
        net.astrorbits.football.network.FootballActionType.PASS,
        net.astrorbits.football.network.FootballActionType.SHOOT,
        -> true
        else -> false
    }

    fun tryNotifyKickoffBallTouched(player: ServerPlayer) {
        if (shouldDeferKickoffTouchForActiveSetPiece()) return
        if (isKickoffInteractionLocked(player)) return
        notifyKickoffBallTouched(player)
    }

    /** 球门球和点球由各自流程在真实触球后推进状态，不由动作包提前解除通用开球锁。 */
    private fun shouldDeferKickoffTouchForActiveSetPiece(): Boolean =
        when (SetPieceState.active?.kind) {
            SetPieceKind.GOAL_KICK,
            SetPieceKind.PENALTY_KICK,
            -> true
            else -> false
        }

    /** 开球锁定倒计时是否仍在进行（全员不可触球/开球）。 */
    fun isKickoffCountdownActive(): Boolean {
        val phaseActive = KickoffLock.isKickoffPhaseActive(activeRestartTeam(), kickoffTouched, kickoffTimerStartMs)
        if (!phaseActive) return false
        return System.currentTimeMillis() - kickoffTimerStartMs < kickoffLockMs
    }

    /**
     * 开球/定位球发球阶段且发球方尚未以踢球等方式合法触球：禁止身体推挤足球。
     * 合法触球后 [kickoffTouched] 为 true，再延迟 [KICKOFF_BODY_CONTACT_RELEASE_DELAY_TICKS] 恢复身体推球。
     */
    fun shouldSuppressKickoffPhaseBodyBallContact(gameTick: Long): Boolean {
        if (kickoffBodyContactReleaseUntilTick >= 0L && gameTick <= kickoffBodyContactReleaseUntilTick) {
            return true
        }
        if (kickoffTouched) return false
        val kickTeam = activeRestartTeam() ?: kickoffTeam ?: return false
        return KickoffLock.isKickoffPhaseActive(kickTeam, kickoffTouched, kickoffTimerStartMs)
    }

    /**
     * 开球/复位阶段且尚未合法触球：滑铲动作允许，但不得以滑铲推动足球（含倒计时结束后发球方提前滑铲蹭球）。
     * 点球阶段（含主罚触球后的 RESOLVING）全程禁止滑铲动球。
     */
    fun shouldSuppressKickoffPhaseSlideBallContact(player: ServerPlayer): Boolean =
        PenaltyFoulGoalWatchState.isActive() ||
            isPenaltyKickSetPieceActive() ||
            shouldSuppressKickoffPhaseBodyBallContact(player.level().gameTime)

    /** 正赛点球或点球大战进行中。 */
    fun isPenaltyKickSetPieceActive(): Boolean =
        PenaltyShootoutState.isActive() || MatchPenaltyKickState.isActive()

    /** 进入开球锁定阶段（重置触球标记与开球哨计时）。 */
    fun beginKickoffPhase(lockMs: Long, context: KickoffWhistleContext) {
        kickoffTouched = false
        kickoffBodyContactReleaseUntilTick = -1L
        kickoffTimerStartMs = System.currentTimeMillis()
        kickoffLockMs = lockMs
        kickoffWhistleContext = context
        kickoffCountdownEndHandled = false
        kickoffWhistle3Played = false
        kickoffWhistle5Played = false
        lastDynamicStoppageAccumMs = 0L
    }

    private fun scheduleKickoffBodyContactRelease(touchGameTick: Long) {
        kickoffBodyContactReleaseUntilTick = touchGameTick + KICKOFF_BODY_CONTACT_RELEASE_DELAY_TICKS
    }

    /** 点球开踢：接入拖延哨（3/5）与正赛动态补时累积。 */
    fun beginPenaltyKickWhistlePhase(kickTeam: TeamSide) {
        kickoffTeam = kickTeam
        beginKickoffPhase(PenaltyShootoutTiming.KICK_INTRO_LOCK_MS, KickoffWhistleContext.PENALTY_KICK)
    }

    fun clearKickoffWhistleTimers() {
        kickoffTimerStartMs = 0L
        kickoffLockMs = 0L
        kickoffWhistleContext = null
        kickoffCountdownEndHandled = false
        kickoffWhistle3Played = false
        kickoffWhistle5Played = false
        lastDynamicStoppageAccumMs = 0L
    }

    /**
     * 开球锁定时长 + 10s 宽限过后仍未触球，则累积动态补时。
     */
    fun tickDynamicStoppageAccumulation() {
        if (postGoalResetPending) return
        if (currentPhase == MatchPhase.PENALTIES) return
        if (kickoffTeam == null || kickoffTouched || kickoffTimerStartMs == 0L) return
        val now = System.currentTimeMillis()
        val graceEnd = kickoffTimerStartMs + kickoffLockMs + MatchKickoffTiming.LATE_KICKOFF_WARN_MS
        if (now <= graceEnd) return
        val maxTicks = MatchConfigHolder.current.stoppageTimeMaxMinutes * 60 * 20
        if (dynamicStoppageTicks >= maxTicks) return
        if (lastDynamicStoppageAccumMs == 0L) lastDynamicStoppageAccumMs = graceEnd
        val delta = now - lastDynamicStoppageAccumMs
        if (delta < 50) return
        val ticks = (delta / 50).toInt().coerceAtMost(maxTicks - dynamicStoppageTicks)
        dynamicStoppageTicks += ticks
        lastDynamicStoppageAccumMs += ticks * 50L
    }

    /**
     * 倒计时结束：仅进球后开球吹 whistle_1；出界开球不吹。
     * 倒计时结束 +10s 未触球 whistle_3；再 +10s whistle_5。
     */
    fun tickKickoffWhistles(server: MinecraftServer) {
        if (kickoffTeam == null || kickoffTouched || kickoffTimerStartMs == 0L) return
        val elapsed = System.currentTimeMillis() - kickoffTimerStartMs
        if (!kickoffCountdownEndHandled && elapsed >= kickoffLockMs) {
            kickoffCountdownEndHandled = true
            if (kickoffWhistleContext == KickoffWhistleContext.POST_GOAL ||
                kickoffWhistleContext == KickoffWhistleContext.PENALTY_KICK
            ) {
                FootballSounds.playMatchWhistle(server, 1)
            }
        }
        val warn3At = kickoffLockMs + MatchKickoffTiming.LATE_KICKOFF_WARN_MS
        if (!kickoffWhistle3Played && elapsed >= warn3At) {
            kickoffWhistle3Played = true
            FootballSounds.playMatchWhistle(server, 3)
        }
        val warn5At = kickoffLockMs + MatchKickoffTiming.LATE_KICKOFF_WARN_MS * 2
        if (!kickoffWhistle5Played && elapsed >= warn5At) {
            kickoffWhistle5Played = true
            FootballSounds.playMatchWhistle(server, 5)
        }
    }

    /** 发球方已触球：广播解锁非发球方（由 [tryNotifyKickoffBallTouched] 在通过锁判定后调用）。 */
    fun notifyKickoffBallTouched(player: ServerPlayer) {
        if (kickoffTouched) return
        val kt = activeRestartTeam() ?: return
        if (MatchParticipation.participatingTeam(player) != kt) return
        kickoffTouched = true
        scheduleKickoffBodyContactRelease(player.level().gameTime)
        val maxTicks = MatchConfigHolder.current.stoppageTimeMaxMinutes * 60 * 20
        if (dynamicStoppageTicks > maxTicks) {
            dynamicStoppageTicks = maxTicks
        }
        clearKickoffWhistleTimers()
        if (!SecondTouchTracker.isActive()) {
            SecondTouchTracker.beginFromKickoffTouch(player)
        }
        val server = player.level().server
        when (SetPieceState.active?.kind) {
            SetPieceKind.CENTER_KICKOFF, SetPieceKind.CORNER_KICK, SetPieceKind.FREE_KICK -> {
                SetPieceState.clear()
                FootballNetworking.broadcastSetPieceState(server)
            }
            else -> Unit
        }
        FootballNetworking.broadcastKickoffBallTouched(server)
    }

    fun matchFieldLevel(server: MinecraftServer): ServerLevel = server.overworld()

    /** 进入新半场/加时/点球等会重新摆球的阶段前，先清掉场上多余足球。 */
    private fun shouldClearFootballsBeforePhaseBallPlacement(phase: MatchPhase): Boolean = when (phase) {
        MatchPhase.FIRST_HALF,
        MatchPhase.SECOND_HALF,
        MatchPhase.EXTRA_FIRST,
        MatchPhase.EXTRA_SECOND,
        MatchPhase.PENALTIES,
        MatchPhase.PRE_MATCH_PREP,
        MatchPhase.FINISHED,
        -> true
        else -> false
    }

    /**
     * 进入赛前准备或结算：取消待复位任务、放下持球并清除全场足球。
     * 避免延迟复位在清场后再次生成上一场遗留的球。
     */
    private fun clearFootballsLeavingPlay(server: MinecraftServer) {
        for (level in server.allLevels) {
            DeferredBallResetScheduler.cancel(level.dimension())
            PostGoalBallResetScheduler.cancel(level.dimension())
        }
        postGoalResetPending = false
        PenaltyFoulGoalWatchState.clear()
        for (player in server.playerList.players) {
            GoalkeeperUtil.findHeldFootball(player)?.dropAt(player)
        }
        clearAllFootballs(server)
    }

    /** 清除服务器所有维度上的足球实体（持球状态一并释放）。 */
    fun clearAllFootballs(server: MinecraftServer) {
        for (level in server.allLevels) {
            for (football in level.getEntitiesOfClass(Football::class.java, ALL_FOOTBALLS_AABB).toList()) {
                football.releaseHold()
                football.discard()
            }
        }
    }

    /** 清除场上所有足球并在开球点（或指定位置）放置一个新足球。 */
    fun resetFootball(level: ServerLevel, pos: Vec3? = null) {
        DeferredBallResetScheduler.cancel(level.dimension())
        PostGoalBallResetScheduler.cancel(level.dimension())
        postGoalResetPending = false
        PenaltyFoulGoalWatchState.clear()
        clearAllFootballs(level.server)
        val fb = Football(Football.ENTITY_TYPE, level)
        val p = pos ?: MatchConfigHolder.current.kickOff.let { Vec3(it.x, it.y, it.z) }
        fb.setPos(p.x, p.y, p.z)
        level.addFreshEntity(fb)
        onRestrictedRestartBallPlaced()
    }

    /** 清除场上所有足球并在指定位置放置一个新足球。 */
    fun resetFootballAt(level: ServerLevel, pos: Vec3) {
        resetFootball(level, pos)
    }

    /** 开赛：可选赛前准备 → 常规流程；或配置为 0 分钟且无加时时直接进入点球大战。配置无效时返回 false。 */
    fun beginMatch(server: MinecraftServer, level: ServerLevel): Boolean {
        if (MatchConfigHolder.current.hasNoPlayableDuration()) {
            return false
        }
        // 结算阶段或进行中再次开赛：重置比分/阶段等，但保留双方名单（见 reset()）。
        if (currentPhase != MatchPhase.PRE_MATCH) {
            reset()
        }
        syncOnlineRostersFromScoreboard(server)
        teleportTeamsToSpawnPositions(server)
        activateSpectators(server)
        val cfg = MatchConfigHolder.current
        if (cfg.startsWithPenaltyShootout()) {
            PlayerRoleState.assignGoalkeepersIfMissing(server)
            broadcastGoalkeepersAnnouncement(server)
            StaminaState.onMatchStart(server)
            setPhase(MatchPhase.PENALTIES, server)
            FootballNetworking.syncTimerToClients(server)
            return true
        }
        if (cfg.isPreMatchPreparationEnabled()) {
            beginPreMatchPreparation(server)
            return true
        }
        startRegularMatch(server)
        return true
    }

    /** 进入赛前准备：清除场上足球并在双方点球点各放置练习球。 */
    fun beginPreMatchPreparation(server: MinecraftServer) {
        PlayerRoleState.assignGoalkeepersIfMissing(server)
        broadcastGoalkeepersAnnouncement(server)
        setPhase(MatchPhase.PRE_MATCH_PREP, server)
        placePreMatchPreparationFootballs(matchFieldLevel(server))
        broadcastPreparationStarted(server)
        FootballNetworking.syncTimerToClients(server)
    }

    /** 赛前准备：在 A/B 球门点球点各生成一颗练习足球（清场由 [setPhase] 完成）。 */
    private fun placePreMatchPreparationFootballs(level: ServerLevel) {
        val config = MatchConfigHolder.current
        for (side in TeamSide.entries) {
            val spot = MatchFieldAreaUtil.goalForSide(config, side).resolvedPenaltySpot().toVec3()
            val football = Football(Football.ENTITY_TYPE, level)
            football.setPos(spot.x, spot.y, spot.z)
            level.addFreshEntity(football)
        }
    }

    /** 准备时间结束后进入上半场并执行常规开赛流程。 */
    fun finishPreMatchPreparation(server: MinecraftServer) {
        if (currentPhase != MatchPhase.PRE_MATCH_PREP) return
        startRegularMatch(server)
    }

    /** 上半场开球：分配/同步守门员、传送至出生点并广播开赛 HUD。 */
    fun startRegularMatch(server: MinecraftServer) {
        PlayerRoleState.assignGoalkeepersIfMissing(server)
        teleportTeamsToSpawnPositions(server)
        broadcastGoalkeepersAnnouncement(server)
        val kickoff = TeamSide.entries.random()
        setPhase(MatchPhase.FIRST_HALF, server)
        broadcastMatchStart(server, kickoff)
        FootballNetworking.syncTimerToClients(server)
    }

    /** 向双方队员聊天栏公示各队守门员。 */
    fun broadcastGoalkeepersAnnouncement(server: MinecraftServer) {
        for (team in TeamSide.entries) {
            val teamName = getTeamName(team)
            val gkUuid = when (team) {
                TeamSide.A -> PlayerRoleState.teamAGoalkeeper
                TeamSide.B -> PlayerRoleState.teamBGoalkeeper
            }
            val gkName = gkUuid?.let { server.playerList.getPlayer(it)?.gameProfile?.name }
                ?.let { Component.literal(it).withColor(ChatFormatting.GREEN.color!!) }
                ?: Component.translatable("match.announce.goalkeeper_unassigned").withColor(ChatFormatting.LIGHT_PURPLE.color!!)
            val message = Component.translatable("match.announce.goalkeeper", teamName, gkName).withColor(ChatFormatting.YELLOW.color!!)
            broadcastToMatchPlayers(server, message)
        }
    }

    private fun broadcastPreparationStarted(server: MinecraftServer) {
        val minutes = MatchConfigHolder.current.preMatchPreparationMinutes
        val matchPrepMsg = Component.empty()
            .append(Component.translatable(
                "match.prep.started.0",
                Component.literal(minutes.toString()).withColor(ChatFormatting.LIGHT_PURPLE.color!!)
            ).withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)))
            .append(Component.translatable("match.prep.started.1").withColor(ChatFormatting.YELLOW.color!!))

        broadcastToMatchPlayers(server, matchPrepMsg)
    }

    private fun broadcastToMatchPlayers(server: MinecraftServer, message: Component) {
        for (uuid in teamAPlayers + teamBPlayers + spectatorPlayers) {
            server.playerList.getPlayer(uuid)?.sendSystemMessage(message)
        }
    }

    /** 点球大战开始前向双方在线队员同步客户端本队身份（不触发开球锁定）。 */
    private fun syncPlayerTeamsToClients(server: MinecraftServer) {
        val nameA = getTeamName(TeamSide.A).string
        val nameB = getTeamName(TeamSide.B).string
        for (uuid in teamAPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            if (!MatchParticipation.isParticipating(player)) continue
            FootballNetworking.sendMatchStart(
                player,
                TeamSide.A,
                PlayerRoleState.isGoalkeeper(player),
                TeamSide.A,
                nameA,
                nameB,
            )
        }
        for (uuid in teamBPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            if (!MatchParticipation.isParticipating(player)) continue
            FootballNetworking.sendMatchStart(
                player,
                TeamSide.B,
                PlayerRoleState.isGoalkeeper(player),
                TeamSide.A,
                nameA,
                nameB,
            )
        }
    }

    /** 向双方在线队员广播比赛开始 HUD 信息。 */
    fun broadcastMatchStart(server: MinecraftServer, kickoff: TeamSide) {
        kickoffTeam = kickoff
        lastHalfKickoffTeam = kickoff
        postGoalResetPending = false
        beginKickoffPhase(MatchKickoffTiming.MATCH_START_LOCK_MS, KickoffWhistleContext.MATCH_START)
        val level = matchFieldLevel(server)
        val kickPos = MatchConfigHolder.current.kickOff.let { Vec3(it.x, it.y, it.z) }
        DeferredBallResetScheduler.schedule(level, kickPos) { loadedLevel ->
            SetPieceBootstrap.onCenterKickoffBegin(kickoff, kickPos, server)
            FootballNetworking.broadcastSetPieceState(server)
        }
        FootballSounds.playMatchWhistle(server, 1)
        StaminaState.onMatchStart(server)
        val nameA = getTeamName(TeamSide.A).string
        val nameB = getTeamName(TeamSide.B).string
        for (uuid in teamAPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            val isGk = PlayerRoleState.isGoalkeeper(player)
            FootballNetworking.sendMatchStart(player, TeamSide.A, isGk, kickoff, nameA, nameB)
        }
        for (uuid in teamBPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            val isGk = PlayerRoleState.isGoalkeeper(player)
            FootballNetworking.sendMatchStart(player, TeamSide.B, isGk, kickoff, nameA, nameB)
        }
    }

    fun teleportTeamsToSpawnPositions(server: MinecraftServer) {
        val config = MatchConfigHolder.current

        for (side in TeamSide.entries) {
            val uuids = when (side) {
                TeamSide.A -> teamAPlayers
                TeamSide.B -> teamBPlayers
            }
            val spawnCfg = when (side) {
                TeamSide.A -> config.teamASpawn
                TeamSide.B -> config.teamBSpawn
            }
            val gkUuid = when (side) {
                TeamSide.A -> PlayerRoleState.teamAGoalkeeper
                TeamSide.B -> PlayerRoleState.teamBGoalkeeper
            }
            teleportTeam(side, uuids, gkUuid, spawnCfg, server)
        }
    }

    fun teleportPlayerToTeamSpawn(player: ServerPlayer, team: TeamSide) {
        val config = MatchConfigHolder.current
        val spawnCfg = when (team) {
            TeamSide.A -> config.teamASpawn
            TeamSide.B -> config.teamBSpawn
        }
        val gkUuid = when (team) {
            TeamSide.A -> PlayerRoleState.teamAGoalkeeper
            TeamSide.B -> PlayerRoleState.teamBGoalkeeper
        }
        val pos = if (player.uuid == gkUuid) {
            spawnCfg.gk
        } else {
            spawnCfg.players.firstOrNull() ?: spawnCfg.gk
        }
        teleportTo(player, pos)
    }

    fun teleportPlayerToTeamCornerFarFromBall(player: ServerPlayer, team: TeamSide, server: MinecraftServer) {
        val ball = ballPositionForSpawnChoice(server)
        val corner = MatchFieldAreaUtil.farthestTeamCornerKickFrom(team, ball.x, ball.z)
        val center = MatchConfigHolder.current.kickOff
        val yaw = Math.toDegrees(kotlin.math.atan2(-(center.x - corner.x), center.z - corner.z)).toFloat()
        teleportToKickPosition(player, corner, yaw)
    }

    private fun ballPositionForSpawnChoice(server: MinecraftServer): Vec3 {
        val level = server.overworld()
        val football = level.getEntitiesOfClass(Football::class.java, ALL_FOOTBALLS_AABB).firstOrNull()
        if (football != null) return football.position()
        val kickOff = MatchConfigHolder.current.kickOff
        return Vec3(kickOff.x, kickOff.y, kickOff.z)
    }

    private fun teleportToKickPosition(player: ServerPlayer, pos: KickPosition, yaw: Float, pitch: Float = 0f) {
        val level = player.level()
        player.teleportTo(level, pos.x, pos.y, pos.z, HashSet(), yaw, pitch, false)
    }

    private fun teleportTeam(
        side: TeamSide,
        uuids: Set<UUID>,
        gkUuid: UUID?,
        spawnCfg: TeamSpawnConfig,
        server: MinecraftServer,
    ) {
        val online = MatchParticipation.onlineParticipating(server, uuids)
        if (online.isEmpty()) return

        val gk = gkUuid?.let { server.playerList.getPlayer(it) }?.takeIf { MatchParticipation.isParticipating(it) }
        // 门将传至 GK 出生点
        gk?.let { teleportTo(it, spawnCfg.gk) }

        // 普通队员（排除门将）
        val outfield = online.filter { it.uuid != gkUuid }.shuffled()
        if (outfield.isEmpty()) return

        val positions = spawnCfg.players
        if (positions.isEmpty()) return

        // 每个坐标至少分配一人
        for (i in positions.indices) {
            if (i < outfield.size) {
                teleportTo(outfield[i], positions[i])
            }
        }

        // 剩余队员随机分配
        if (outfield.size > positions.size) {
            for (i in positions.size until outfield.size) {
                teleportTo(outfield[i], positions.random())
            }
        }
    }

    private fun teleportTo(player: ServerPlayer, pos: SpawnPosition) {
        val level = player.level()
        player.teleportTo(level, pos.x, pos.y, pos.z, HashSet(), pos.yaw, pos.pitch, false)
    }

    /** 正计时格式化 (从 0 向上) */
    fun formatTime(): String {
        val totalSeconds = timerTicks / 20
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    /** 格式化 stoppage 计时 */
    fun formatStoppageTime(): String {
        val totalSeconds = stoppageTimerTicks / 20
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "+%02d:%02d".format(minutes, seconds)
    }

    /** 当前阶段的目标时间（累积 tick），用于 HUD 显示 "elapsed / target"。无目标返回 -1。
     * 补时阶段返回其父阶段的终止时间，确保进入补时后主终止时间不变。 */
    fun getPhaseTargetTicks(): Int {
        val config = MatchConfigHolder.current
        val halfDuration = config.halfTimeMinutes * 60 * 20
        val extraDuration = config.extraTimeHalfMinutes * 60 * 20
        return when (currentPhase) {
            MatchPhase.PRE_MATCH, MatchPhase.FINISHED -> -1
            MatchPhase.PRE_MATCH_PREP -> config.rules.preMatchPreparationTicks()
            MatchPhase.FIRST_HALF, MatchPhase.FIRST_HALF_ET -> halfDuration
            MatchPhase.SECOND_HALF, MatchPhase.SECOND_HALF_ET -> halfDuration * 2
            MatchPhase.EXTRA_FIRST, MatchPhase.EXTRA_FIRST_ET -> halfDuration * 2 + extraDuration
            MatchPhase.EXTRA_SECOND, MatchPhase.EXTRA_SECOND_ET -> halfDuration * 2 + extraDuration * 2
            MatchPhase.PENALTIES -> -1
        }
    }

    /** 补时阶段的最大时长（tick），仅用于补时面板的 "+MM:SS" 目标显示 */
    fun getStoppageTargetTicks(): Int {
        return if (dynamicStoppageTicks > 0) dynamicStoppageTicks else MatchConfigHolder.current.stoppageTimeMaxMinutes * 60 * 20
    }

    /** 主计时器显示用 tick（补时期间主计时器冻结，返回 timerTicks） */
    fun getPhaseDisplayTicks(): Int = timerTicks

    /** 阶段剩余 tick（用于自动推进判断） */
    fun getPhaseRemainingTicks(): Int {
        val config = MatchConfigHolder.current
        val halfDuration = config.halfTimeMinutes * 60 * 20
        val extraDuration = config.extraTimeHalfMinutes * 60 * 20
        val stoppageDuration = getStoppageTargetTicks()
        return when (currentPhase) {
            MatchPhase.PRE_MATCH_PREP -> {
                val target = config.rules.preMatchPreparationTicks()
                (target - timerTicks).coerceAtLeast(0)
            }
            MatchPhase.FIRST_HALF -> (halfDuration - timerTicks).coerceAtLeast(0)
            MatchPhase.FIRST_HALF_ET -> (stoppageDuration - stoppageTimerTicks).coerceAtLeast(0)
            MatchPhase.SECOND_HALF -> (halfDuration * 2 - timerTicks).coerceAtLeast(0)
            MatchPhase.SECOND_HALF_ET -> (stoppageDuration - stoppageTimerTicks).coerceAtLeast(0)
            MatchPhase.EXTRA_FIRST -> (halfDuration * 2 + extraDuration - timerTicks).coerceAtLeast(0)
            MatchPhase.EXTRA_FIRST_ET -> (stoppageDuration - stoppageTimerTicks).coerceAtLeast(0)
            MatchPhase.EXTRA_SECOND -> (halfDuration * 2 + extraDuration * 2 - timerTicks).coerceAtLeast(0)
            MatchPhase.EXTRA_SECOND_ET -> (stoppageDuration - stoppageTimerTicks).coerceAtLeast(0)
            else -> Int.MAX_VALUE
        }
    }

    fun isStoppagePhase(): Boolean {
        return currentPhase == MatchPhase.FIRST_HALF_ET || currentPhase == MatchPhase.SECOND_HALF_ET
            || currentPhase == MatchPhase.EXTRA_FIRST_ET || currentPhase == MatchPhase.EXTRA_SECOND_ET
    }

    fun advancePhase(server: MinecraftServer? = null): MatchPhase {
        val next = currentPhase.next ?: return currentPhase
        setPhase(next, server)
        return next
    }

    fun setPhase(phase: MatchPhase, server: MinecraftServer? = null) {
        val wasPenalties = currentPhase == MatchPhase.PENALTIES
        if (wasPenalties && phase != MatchPhase.PENALTIES && phase != MatchPhase.FINISHED) {
            PenaltyShootoutState.clear(server)
        }
        if (server != null && shouldClearFootballsBeforePhaseBallPlacement(phase)) {
            when (phase) {
                MatchPhase.FINISHED, MatchPhase.PRE_MATCH_PREP -> clearFootballsLeavingPlay(server)
                else -> clearAllFootballs(server)
            }
        }
        currentPhase = phase
        stoppageTimerTicks = 0
        // 进入新半场时清零动态补时
        if (phase == MatchPhase.FIRST_HALF || phase == MatchPhase.SECOND_HALF ||
            phase == MatchPhase.EXTRA_FIRST || phase == MatchPhase.EXTRA_SECOND) {
            dynamicStoppageTicks = 0
            lastDynamicStoppageAccumMs = 0L
        }
        if (phase == MatchPhase.SECOND_HALF || phase == MatchPhase.EXTRA_FIRST || phase == MatchPhase.EXTRA_SECOND) {
            halfKickoffBroadcasted = false
        }
        timerTicks = when (phase) {
            MatchPhase.PRE_MATCH, MatchPhase.PRE_MATCH_PREP -> 0
            MatchPhase.FIRST_HALF -> 0
            MatchPhase.FIRST_HALF_ET -> {
                val halfDuration = MatchConfigHolder.current.halfTimeMinutes * 60 * 20
                timerTicks.coerceAtMost(halfDuration)
            }
            MatchPhase.SECOND_HALF -> {
                val halfDuration = MatchConfigHolder.current.halfTimeMinutes * 60 * 20
                halfDuration
            }
            MatchPhase.SECOND_HALF_ET -> {
                val halfDuration = MatchConfigHolder.current.halfTimeMinutes * 60 * 20
                timerTicks.coerceAtMost(halfDuration * 2)
            }
            MatchPhase.EXTRA_FIRST -> {
                val halfDuration = MatchConfigHolder.current.halfTimeMinutes * 60 * 20
                halfDuration * 2
            }
            MatchPhase.EXTRA_FIRST_ET -> {
                val config = MatchConfigHolder.current
                val halfDuration = config.halfTimeMinutes * 60 * 20
                val extraDuration = config.extraTimeHalfMinutes * 60 * 20
                timerTicks.coerceAtMost(halfDuration * 2 + extraDuration)
            }
            MatchPhase.EXTRA_SECOND -> {
                val config = MatchConfigHolder.current
                val halfDuration = config.halfTimeMinutes * 60 * 20
                val extraDuration = config.extraTimeHalfMinutes * 60 * 20
                halfDuration * 2 + extraDuration
            }
            MatchPhase.EXTRA_SECOND_ET -> {
                val config = MatchConfigHolder.current
                val halfDuration = config.halfTimeMinutes * 60 * 20
                val extraDuration = config.extraTimeHalfMinutes * 60 * 20
                timerTicks.coerceAtMost(halfDuration * 2 + extraDuration * 2)
            }
            MatchPhase.PENALTIES -> 0
            MatchPhase.FINISHED -> timerTicks
        }
        isRunning = phase != MatchPhase.PRE_MATCH && phase != MatchPhase.FINISHED && phase != MatchPhase.PENALTIES
        if (server != null) {
            PlayerRoleState.syncRolesToOnlinePlayers(server)
        }
        if (phase == MatchPhase.FINISHED && server != null) {
            restoreSpectators(server)
            MatchSendOffState.restoreAllForMatchEnd(server)
        }
        if (phase == MatchPhase.PENALTIES && server != null && teamAScore == teamBScore) {
            syncPlayerTeamsToClients(server)
            PenaltyShootoutState.start(server)
        }
        if (server != null &&
            (phase == MatchPhase.SECOND_HALF || phase == MatchPhase.EXTRA_FIRST || phase == MatchPhase.EXTRA_SECOND)
        ) {
            FootballNetworking.triggerHalfKickoff(server, matchFieldLevel(server))
            FootballNetworking.syncTimerToClients(server)
        }
    }

    /** 根据当前阶段和配置自动判断下一步应该进入什么阶段 */
    fun getNextPhaseForAutoAdvance(): MatchPhase? {
        val config = MatchConfigHolder.current
        return when (currentPhase) {
            MatchPhase.PRE_MATCH_PREP -> MatchPhase.FIRST_HALF
            MatchPhase.FIRST_HALF -> if (config.enableStoppageTime && dynamicStoppageTicks > 0) MatchPhase.FIRST_HALF_ET else MatchPhase.SECOND_HALF
            MatchPhase.SECOND_HALF -> {
                if (config.enableStoppageTime && dynamicStoppageTicks > 0) MatchPhase.SECOND_HALF_ET
                else if (config.enableExtraTime && teamAScore == teamBScore) MatchPhase.EXTRA_FIRST
                else if (config.enablePenaltyShootout && teamAScore == teamBScore) MatchPhase.PENALTIES
                else MatchPhase.FINISHED
            }
            MatchPhase.SECOND_HALF_ET -> {
                if (config.enableExtraTime && teamAScore == teamBScore) MatchPhase.EXTRA_FIRST
                else if (config.enablePenaltyShootout && teamAScore == teamBScore) MatchPhase.PENALTIES
                else MatchPhase.FINISHED
            }
            MatchPhase.EXTRA_FIRST -> if (config.enableStoppageTime && dynamicStoppageTicks > 0) MatchPhase.EXTRA_FIRST_ET else MatchPhase.EXTRA_SECOND
            MatchPhase.EXTRA_FIRST_ET -> MatchPhase.EXTRA_SECOND
            MatchPhase.EXTRA_SECOND -> {
                if (config.enableStoppageTime && dynamicStoppageTicks > 0) MatchPhase.EXTRA_SECOND_ET
                else if (config.enablePenaltyShootout && teamAScore == teamBScore) MatchPhase.PENALTIES
                else MatchPhase.FINISHED
            }
            MatchPhase.EXTRA_SECOND_ET -> {
                if (config.enablePenaltyShootout && teamAScore == teamBScore) MatchPhase.PENALTIES
                else MatchPhase.FINISHED
            }
            else -> currentPhase.next
        }
    }

    /** 主 HUD 栏显示的阶段（补时阶段显示其父阶段，如 FIRST_HALF_ET → FIRST_HALF） */
    fun getMainDisplayPhase(): MatchPhase = when (currentPhase) {
        MatchPhase.FIRST_HALF_ET -> MatchPhase.FIRST_HALF
        MatchPhase.SECOND_HALF_ET -> MatchPhase.SECOND_HALF
        MatchPhase.EXTRA_FIRST_ET -> MatchPhase.EXTRA_FIRST
        MatchPhase.EXTRA_SECOND_ET -> MatchPhase.EXTRA_SECOND
        else -> currentPhase
    }

    /** 当前阶段名称的翻译键 */
    fun getPhaseDisplayName(): Component {
        return Component.translatable(currentPhase.displayNameKey)
    }

    /** 补时计时器：返回 "01:15(+03:00)" 格式（已用时间 + 补时上限） */
    fun formatStoppageWithTarget(): String {
        val elapsed = formatTicks(stoppageTimerTicks)
        val targetTicks = getStoppageTargetTicks()
        if (targetTicks <= 0) return elapsed
        val target = formatTicks(targetTicks)
        return "$elapsed(+$target)"
    }

    /** 格式化已用时间 */
    fun formatElapsed(ticks: Int): String = formatTicks(ticks)

    /** 阶段终止时间（目标时间），无目标时返回空字符串 */
    fun formatPhaseEndTime(): String {
        val target = getPhaseTargetTicks()
        if (target <= 0) return ""
        return formatTicks(target)
    }
}

private fun formatTicks(ticks: Int): String {
    val totalSeconds = ticks / 20
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
