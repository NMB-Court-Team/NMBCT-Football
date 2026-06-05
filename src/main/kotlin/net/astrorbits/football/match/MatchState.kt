package net.astrorbits.football.match

import net.astrorbits.football.Football
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.stamina.StaminaState
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID

object MatchState {
    private val DEFAULT_TEAM_A_NAME = Component.translatable("team_name.nmbct-football.teamA").withStyle(ChatFormatting.RED)
    private val DEFAULT_TEAM_B_NAME = Component.translatable("team_name.nmbct-football.teamB").withStyle(ChatFormatting.BLUE)

    private const val SCOREBOARD_TEAM_A = "football_A"
    private const val SCOREBOARD_TEAM_B = "football_B"

    var timerTicks = 0
    var stoppageTimerTicks = 0
    var isRunning = true
    var currentPhase: MatchPhase = MatchPhase.PRE_MATCH
    var teamAName: Component = DEFAULT_TEAM_A_NAME
    var teamBName: Component = DEFAULT_TEAM_B_NAME
    var teamAScore = 0
    var teamBScore = 0
    val teamAPlayers: MutableSet<UUID> = mutableSetOf()
    val teamBPlayers: MutableSet<UUID> = mutableSetOf()
    var kickoffTeam: TeamSide? = null
    var kickoffTouched: Boolean = false
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

    /** 比赛计时是否暂停（含赛前准备阶段，即 [isRunning] 为 false 且仍在可计时阶段）。 */
    fun isMatchTimerPaused(): Boolean =
        (isDuringMatch() || isPreMatchPreparationPhase()) && !isRunning

    fun addPlayer(team: TeamSide, uuid: UUID) {
        when (team) {
            TeamSide.A -> teamAPlayers.add(uuid)
            TeamSide.B -> teamBPlayers.add(uuid)
        }
    }

    fun removePlayer(uuid: UUID): Boolean {
        return teamAPlayers.remove(uuid) || teamBPlayers.remove(uuid)
    }

    fun syncPlayerScoreboard(uuid: UUID, team: TeamSide?, server: MinecraftServer) {
        val player = server.playerList.getPlayer(uuid) ?: return
        val playerName = player.gameProfile.name
        val scoreboard = server.scoreboard

        for (teamKey in listOf(SCOREBOARD_TEAM_A, SCOREBOARD_TEAM_B)) {
            val sbTeam = scoreboard.getPlayerTeam(teamKey) ?: continue
            if (sbTeam.players.contains(playerName)) {
                scoreboard.removePlayerFromTeam(playerName, sbTeam)
            }
        }

        if (team != null) {
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

    fun clearScoreboardTeams(server: MinecraftServer) {
        val scoreboard = server.scoreboard
        for (teamKey in listOf(SCOREBOARD_TEAM_A, SCOREBOARD_TEAM_B)) {
            scoreboard.getPlayerTeam(teamKey)?.let { team ->
                val players = team.players.toList()
                for (playerName in players) {
                    scoreboard.removePlayerFromTeam(playerName, team)
                }
            }
        }
    }

    fun reset() {
        timerTicks = 0
        stoppageTimerTicks = 0
        currentPhase = MatchPhase.PRE_MATCH
        isRunning = false
        teamAScore = 0
        teamBScore = 0
        kickoffTeam = null
        kickoffTouched = false
        clearKickoffWhistleTimers()
        dynamicStoppageTicks = 0
        lastDynamicStoppageAccumMs = 0L
        halfKickoffBroadcasted = false
        lastHalfKickoffTeam = null
        postGoalResetPending = false
        clearPendingGoalLineOut()
        clearDirectGoalRestriction()
        clearPendingOffsideSnapshot()
        PlayerRoleState.reset()
        PenaltyShootoutState.clear()
        MatchPenaltyKickState.clear()
        SetPieceState.clear()
    }

    fun kickoffWhistleContext(): KickoffWhistleContext? = kickoffWhistleContext

    fun forceKickoffBallTouched() {
        if (kickoffTouched) return
        kickoffTouched = true
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
        if (SetPieceRestrictionCoordinator.isFootballOperationBlocked(player, action)) {
            return true
        }
        if (SetPieceRestrictionCoordinator.isPlayerBallMovementForbidden(player)) {
            return true
        }
        if (ThrowInSetPieceFlow.isMovementFrozen(player)) {
            return true
        }
        if (PenaltyShootoutState.isPenaltyMovementRestricted(player)) {
            return true
        }
        if (MatchPenaltyKickState.isMovementRestricted(player)) {
            return true
        }
        if (currentPhase == MatchPhase.PENALTIES && !PenaltyShootoutState.isPenaltyFootballInteractionAllowed(player)) {
            return true
        }
        if (MatchPenaltyKickState.isActive() && !MatchPenaltyKickState.isFootballInteractionAllowed(player)) {
            return true
        }
        if (action == net.astrorbits.football.network.FootballActionType.GK_CATCH &&
            SetPieceRestrictionCoordinator.allowsGoalKickCatch(player)
        ) {
            return false
        }
        if (action == net.astrorbits.football.network.FootballActionType.GK_DROP &&
            SetPieceState.active?.kind == SetPieceKind.GOAL_KICK &&
            player.uuid == SetPieceState.active?.goalKickPickerUuid
        ) {
            return false
        }
        if ((action == net.astrorbits.football.network.FootballActionType.GK_THROW_SHORT ||
                action == net.astrorbits.football.network.FootballActionType.GK_THROW_LONG) &&
            SetPieceState.active?.kind == SetPieceKind.THROW_IN &&
            player.uuid == SetPieceState.active?.throwInTakerUuid
        ) {
            return false
        }
        val phaseActive = KickoffLock.isKickoffPhaseActive(kickoffTeam, kickoffTouched, kickoffTimerStartMs)
        val elapsed = if (phaseActive) System.currentTimeMillis() - kickoffTimerStartMs else 0L
        return KickoffLock.isPlayerLocked(
            postGoalResetPending = postGoalResetPending,
            kickoffPhaseActive = phaseActive,
            playerTeam = MatchParticipation.participatingTeam(player),
            kickoffTeam = kickoffTeam,
            kickoffElapsedMs = elapsed,
            kickoffLockMs = kickoffLockMs,
        )
    }

    fun tryNotifyKickoffBallTouched(player: ServerPlayer) {
        if (isKickoffInteractionLocked(player)) return
        notifyKickoffBallTouched(player)
    }

    /** 进入开球锁定阶段（重置触球标记与开球哨计时）。 */
    fun beginKickoffPhase(lockMs: Long, context: KickoffWhistleContext) {
        kickoffTouched = false
        kickoffTimerStartMs = System.currentTimeMillis()
        kickoffLockMs = lockMs
        kickoffWhistleContext = context
        kickoffCountdownEndHandled = false
        kickoffWhistle3Played = false
        kickoffWhistle5Played = false
        lastDynamicStoppageAccumMs = 0L
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
            if (kickoffWhistleContext == KickoffWhistleContext.POST_GOAL) {
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
        val kt = kickoffTeam ?: return
        if (MatchParticipation.participatingTeam(player) != kt) return
        kickoffTouched = true
        val maxTicks = MatchConfigHolder.current.stoppageTimeMaxMinutes * 60 * 20
        if (dynamicStoppageTicks > maxTicks) {
            dynamicStoppageTicks = maxTicks
        }
        clearKickoffWhistleTimers()
        val server = player.level().server ?: return
        when (SetPieceState.active?.kind) {
            SetPieceKind.CENTER_KICKOFF, SetPieceKind.CORNER_KICK -> {
                SetPieceState.clear()
                FootballNetworking.broadcastSetPieceState(server)
            }
            else -> Unit
        }
        FootballNetworking.broadcastKickoffBallTouched(server)
    }

    /** 清除场上所有足球并在开球点（或指定位置）放置一个新足球。 */
    fun resetFootball(level: ServerLevel, pos: Vec3? = null) {
        PostGoalBallResetScheduler.cancel(level.dimension())
        postGoalResetPending = false
        val all = AABB(Vec3(-3.0E7, -3.0E7, -3.0E7), Vec3(3.0E7, 3.0E7, 3.0E7))
        level.getEntitiesOfClass(Football::class.java, all).forEach { it.discard() }
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
        resetFootball(level)
        teleportTeamsToSpawnPositions(server)
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

    /** 进入赛前准备：分配缺失的守门员、公示双方门将并开始准备计时。 */
    fun beginPreMatchPreparation(server: MinecraftServer) {
        PlayerRoleState.assignGoalkeepersIfMissing(server)
        broadcastGoalkeepersAnnouncement(server)
        setPhase(MatchPhase.PRE_MATCH_PREP, server)
        broadcastPreparationStarted(server)
        FootballNetworking.syncTimerToClients(server)
    }

    /** 准备时间结束后进入上半场并执行常规开赛流程。 */
    fun finishPreMatchPreparation(server: MinecraftServer) {
        if (currentPhase != MatchPhase.PRE_MATCH_PREP) return
        startRegularMatch(server)
    }

    /** 上半场开球：分配/同步守门员并广播开赛 HUD。 */
    fun startRegularMatch(server: MinecraftServer) {
        PlayerRoleState.assignGoalkeepersIfMissing(server)
        broadcastGoalkeepersAnnouncement(server)
        val kickoff = TeamSide.entries.random()
        setPhase(MatchPhase.FIRST_HALF, server)
        broadcastMatchStart(server, kickoff)
        FootballNetworking.syncTimerToClients(server)
    }

    /** 向双方队员聊天栏公示各队守门员。 */
    fun broadcastGoalkeepersAnnouncement(server: MinecraftServer) {
        for (team in TeamSide.entries) {
            val teamName = getTeamName(team).string
            val gkUuid = when (team) {
                TeamSide.A -> PlayerRoleState.teamAGoalkeeper
                TeamSide.B -> PlayerRoleState.teamBGoalkeeper
            }
            val gkName = gkUuid?.let { server.playerList.getPlayer(it)?.gameProfile?.name }
            val message = if (gkName != null) {
                Component.translatable("match.announce.goalkeeper", teamName, gkName)
            } else {
                Component.translatable("match.announce.goalkeeper_unassigned", teamName)
            }
            broadcastToMatchPlayers(server, message)
        }
    }

    private fun broadcastPreparationStarted(server: MinecraftServer) {
        val minutes = MatchConfigHolder.current.preMatchPreparationMinutes
        broadcastToMatchPlayers(
            server,
            Component.translatable("match.prep.started", minutes),
        )
    }

    private fun broadcastToMatchPlayers(server: MinecraftServer, message: Component) {
        for (uuid in teamAPlayers + teamBPlayers) {
            server.playerList.getPlayer(uuid)?.sendSystemMessage(message)
        }
    }

    /** 向双方在线队员广播比赛开始 HUD 信息。 */
    fun broadcastMatchStart(server: MinecraftServer, kickoff: TeamSide) {
        kickoffTeam = kickoff
        lastHalfKickoffTeam = kickoff
        beginKickoffPhase(MatchKickoffTiming.MATCH_START_LOCK_MS, KickoffWhistleContext.MATCH_START)
        val kickPos = MatchConfigHolder.current.kickOff.let { Vec3(it.x, it.y, it.z) }
        SetPieceBootstrap.onCenterKickoffBegin(kickoff, kickPos)
        FootballNetworking.broadcastSetPieceState(server)
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
        val level = player.level() ?: return
        player.teleportTo(level, pos.x, pos.y, pos.z, java.util.HashSet(), pos.yaw, pos.pitch, false)
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
        if (phase == MatchPhase.PENALTIES && server != null && teamAScore == teamBScore) {
            PenaltyShootoutState.start(server)
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
