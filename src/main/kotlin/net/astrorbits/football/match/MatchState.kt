package net.astrorbits.football.match

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
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

    fun getTeamName(team: TeamSide): Component = when (team) {
        TeamSide.A -> teamAName.copy().withStyle(ChatFormatting.RED)
        TeamSide.B -> teamBName.copy().withStyle(ChatFormatting.AQUA)
    }

    fun getPlayerTeam(uuid: UUID): TeamSide? = when {
        teamAPlayers.contains(uuid) -> TeamSide.A
        teamBPlayers.contains(uuid) -> TeamSide.B
        else -> null
    }

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
        teamAPlayers.clear()
        teamBPlayers.clear()
        PlayerRoleState.reset()
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

    /** 开赛时将双方队员传送至出生点。守门员传至 GK 点，其余队员按"每个坐标至少一人"分配。 */
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
        val online = uuids.mapNotNull { server.playerList.getPlayer(it) }
        if (online.isEmpty()) return

        val gk = gkUuid?.let { server.playerList.getPlayer(it) }
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
        val level = player.level() as? ServerLevel ?: return
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
            MatchPhase.FIRST_HALF, MatchPhase.FIRST_HALF_ET -> halfDuration
            MatchPhase.SECOND_HALF, MatchPhase.SECOND_HALF_ET -> halfDuration * 2
            MatchPhase.EXTRA_FIRST, MatchPhase.EXTRA_FIRST_ET -> halfDuration * 2 + extraDuration
            MatchPhase.EXTRA_SECOND, MatchPhase.EXTRA_SECOND_ET -> halfDuration * 2 + extraDuration * 2
            MatchPhase.PENALTIES -> -1
        }
    }

    /** 补时阶段的最大时长（tick），仅用于补时面板的 "+MM:SS" 目标显示 */
    fun getStoppageTargetTicks(): Int {
        return MatchConfigHolder.current.stoppageTimeMaxMinutes * 60 * 20
    }

    /** 主计时器显示用 tick（补时期间主计时器冻结，返回 timerTicks） */
    fun getPhaseDisplayTicks(): Int = timerTicks

    /** 阶段剩余 tick（用于自动推进判断） */
    fun getPhaseRemainingTicks(): Int {
        val config = MatchConfigHolder.current
        val halfDuration = config.halfTimeMinutes * 60 * 20
        val extraDuration = config.extraTimeHalfMinutes * 60 * 20
        val stoppageDuration = config.stoppageTimeMaxMinutes * 60 * 20
        return when (currentPhase) {
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

    fun advancePhase(): MatchPhase {
        val next = currentPhase.next ?: return currentPhase
        setPhase(next)
        return next
    }

    fun setPhase(phase: MatchPhase) {
        currentPhase = phase
        stoppageTimerTicks = 0
        timerTicks = when (phase) {
            MatchPhase.PRE_MATCH -> 0
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
        isRunning = phase != MatchPhase.PRE_MATCH && phase != MatchPhase.FINISHED
    }

    /** 根据当前阶段和配置自动判断下一步应该进入什么阶段 */
    fun getNextPhaseForAutoAdvance(): MatchPhase? {
        val config = MatchConfigHolder.current
        return when (currentPhase) {
            MatchPhase.FIRST_HALF -> if (config.enableStoppageTime) MatchPhase.FIRST_HALF_ET else MatchPhase.SECOND_HALF
            MatchPhase.SECOND_HALF -> {
                if (config.enableStoppageTime) MatchPhase.SECOND_HALF_ET
                else if (config.enableExtraTime) MatchPhase.EXTRA_FIRST
                else if (config.enablePenaltyShootout) MatchPhase.PENALTIES
                else MatchPhase.FINISHED
            }
            MatchPhase.SECOND_HALF_ET -> {
                if (config.enableExtraTime) MatchPhase.EXTRA_FIRST
                else if (config.enablePenaltyShootout) MatchPhase.PENALTIES
                else MatchPhase.FINISHED
            }
            MatchPhase.EXTRA_FIRST -> if (config.enableStoppageTime) MatchPhase.EXTRA_FIRST_ET else MatchPhase.EXTRA_SECOND
            MatchPhase.EXTRA_FIRST_ET -> MatchPhase.EXTRA_SECOND
            MatchPhase.EXTRA_SECOND -> {
                if (config.enableStoppageTime) MatchPhase.EXTRA_SECOND_ET
                else if (config.enablePenaltyShootout) MatchPhase.PENALTIES
                else MatchPhase.FINISHED
            }
            MatchPhase.EXTRA_SECOND_ET -> {
                if (config.enablePenaltyShootout) MatchPhase.PENALTIES
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
