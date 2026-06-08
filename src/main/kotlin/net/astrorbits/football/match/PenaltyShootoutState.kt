package net.astrorbits.football.match

import net.astrorbits.football.Football
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.network.FootballActionType
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.util.GoalCrossingUtil
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import java.util.*
import kotlin.random.Random

enum class PenaltyKickPhase {
    SETUP,
    AWAITING_KICK,
    RESOLVING,
}

object PenaltyShootoutState {
    private const val REGULATION_KICKS_PER_TEAM = 5
    private const val RESOLVE_TIMEOUT_TICKS = 600
    private const val STATIONARY_SPEED_SQR = 0.002
    private const val STATIONARY_TICKS_NEEDED = 20
    private const val KICKER_OFFSET_BLOCKS = 2.5
    /** 等待区：点球点正上方（旁观模式俯瞰罚球）。 */
    private const val WAITING_SPECTATOR_Y_OFFSET = 5.0

    var active: Boolean = false
        private set
    var penaltyScoreA: Int = 0
        private set
    var penaltyScoreB: Int = 0
        private set
    /** 掷币选定、整场不变的罚球球门所属队伍（IFAB 固定球门）。 */
    var penaltyGoalTeam: TeamSide = TeamSide.A
        private set
    /** 本轮站在固定球门前的防守方（主罚方对手的门将）。 */
    var activeDefendingTeam: TeamSide = TeamSide.A
        private set
    var firstKickTeam: TeamSide = TeamSide.A
        private set
    var suddenDeath: Boolean = false
        private set
    var totalKicksTaken: Int = 0
        private set
    var kicksTakenA: Int = 0
        private set
    var kicksTakenB: Int = 0
        private set
    var currentKickerTeam: TeamSide = TeamSide.A
        private set
    var currentKickerUuid: UUID? = null
        private set
    var kickPhase: PenaltyKickPhase = PenaltyKickPhase.SETUP
        private set
    var lastWinner: TeamSide? = null
        private set

    private val kickedPlayersA = mutableSetOf<UUID>()
    private val kickedPlayersB = mutableSetOf<UUID>()
    /** 点球大战开始时在线的队员；全员至少踢一次后才允许二踢。 */
    private val eligibleKickersA = mutableSetOf<UUID>()
    private val eligibleKickersB = mutableSetOf<UUID>()
    /** 主罚被选次数；优先次数较少的非门将。 */
    private val kickCountByPlayer = mutableMapOf<UUID, Int>()
    private var resolveTicks = 0
    private var stationaryTicks = 0
    private var outcomeRecorded = false
    private var activeFootballId: Int = -1
    private var pendingAdvance = false
    private var kickIntroTicksRemaining = 0
    private val penaltyWaitingSpectators = mutableSetOf<UUID>()
    private val penaltyWaitingPreviousGameModes = mutableMapOf<UUID, GameType>()

    fun isActive(): Boolean = active && MatchState.currentPhase == MatchPhase.PENALTIES

    fun isPenaltyWaitingSpectator(player: ServerPlayer): Boolean =
        player.uuid in penaltyWaitingSpectators

    fun start(server: MinecraftServer) {
        clear()
        active = true
        lastWinner = null
        penaltyGoalTeam = TeamSide.entries[Random.nextInt(TeamSide.entries.size)]
        firstKickTeam = TeamSide.entries[Random.nextInt(TeamSide.entries.size)]
        suddenDeath = false
        penaltyScoreA = 0
        penaltyScoreB = 0
        totalKicksTaken = 0
        kicksTakenA = 0
        kicksTakenB = 0
        MatchState.clearKickoffWhistleTimers()
        MatchState.kickoffTeam = null
        MatchState.kickoffTouched = false
        MatchState.postGoalResetPending = false
        MatchState.clearDirectGoalRestriction()

        captureEligibleKickPools(server)
        FootballSounds.playMatchWhistle(server, 1)
        val goalName = MatchState.getTeamName(penaltyGoalTeam).string
        val firstName = MatchState.getTeamName(firstKickTeam).string
        server.playerList.broadcastSystemMessage(
            Component.translatable("match.penalty.toss", goalName, firstName),
            false,
        )
        beginKick(server)
    }

    fun clear(server: MinecraftServer? = null) {
        MatchState.clearKickoffWhistleTimers()
        MatchState.kickoffTeam = null
        active = false
        penaltyScoreA = 0
        penaltyScoreB = 0
        totalKicksTaken = 0
        kicksTakenA = 0
        kicksTakenB = 0
        suddenDeath = false
        currentKickerUuid = null
        kickPhase = PenaltyKickPhase.SETUP
        kickedPlayersA.clear()
        kickedPlayersB.clear()
        eligibleKickersA.clear()
        eligibleKickersB.clear()
        kickCountByPlayer.clear()
        PlayerRoleState.clearPenaltyKickOutfieldOverrides(server)
        if (SetPieceState.active?.kind == SetPieceKind.PENALTY_KICK) {
            SetPieceState.clear()
            server?.let { FootballNetworking.broadcastSetPieceState(it) }
        }
        resolveTicks = 0
        stationaryTicks = 0
        outcomeRecorded = false
        activeFootballId = -1
        pendingAdvance = false
        kickIntroTicksRemaining = 0
        lastWinner = null
        restoreAllPenaltyWaitingSpectators(server)
    }

    fun defendingGoal(): GoalConfig {
        val config = MatchConfigHolder.current
        return when (penaltyGoalTeam) {
            TeamSide.A -> config.goalA
            TeamSide.B -> config.goalB
        }
    }

    fun isPenaltyFootballInteractionAllowed(player: ServerPlayer): Boolean {
        if (!isActive()) return true
        if (kickPhase == PenaltyKickPhase.SETUP) return false
        if (player.uuid == currentKickerUuid) {
            return kickPhase == PenaltyKickPhase.AWAITING_KICK
        }
        if (isDefendingGoalkeeper(player)) return true
        return false
    }

    /** 主罚仅可在等待开踢时踢球一次（传球/蓄力射门）；其余动作与其它阶段一律拒绝。 */
    fun deniesPenaltyKickerAction(player: ServerPlayer, action: FootballActionType?): Boolean {
        if (!isActive() || player.uuid != currentKickerUuid) return false
        return !allowsPenaltyKickerAction(player, action)
    }

    fun allowsPenaltyKickerAction(player: ServerPlayer, action: FootballActionType?): Boolean {
        if (!isActive() || player.uuid != currentKickerUuid) return false
        if (kickPhase != PenaltyKickPhase.AWAITING_KICK) return false
        return when (action) {
            null,
            FootballActionType.PASS,
            FootballActionType.SHOOT,
            -> true
            else -> false
        }
    }

    fun isPenaltyMovementRestricted(player: ServerPlayer): Boolean {
        if (!isActive()) return false
        if (isPenaltyWaitingSpectator(player)) return false
        if (!MatchParticipation.isParticipating(player)) return false
        if (player.uuid == currentKickerUuid) return false
        if (isDefendingGoalkeeper(player)) return false
        return true
    }

    /** 开踢前/等待主罚：防守门将可按住右键蓄力鱼跃。 */
    fun isPenaltyGoalkeeperDiveChargeAllowed(player: ServerPlayer): Boolean {
        if (!isActive() || !isDefendingGoalkeeper(player)) return false
        return kickPhase == PenaltyKickPhase.SETUP || kickPhase == PenaltyKickPhase.AWAITING_KICK
    }

    /** 主罚触球后或等待开踢：防守门将可释放鱼跃扑救。 */
    fun isPenaltyGoalkeeperDiveExecutionAllowed(player: ServerPlayer): Boolean {
        if (!isActive() || !isDefendingGoalkeeper(player)) return false
        return kickPhase == PenaltyKickPhase.AWAITING_KICK || kickPhase == PenaltyKickPhase.RESOLVING
    }

    fun allowsPenaltyGoalkeeperAction(player: ServerPlayer, action: FootballActionType?): Boolean =
        when (action) {
            FootballActionType.GK_DIVE_CHARGE_DRAIN,
            FootballActionType.GK_DIVE_CHARGE_CANCEL,
            -> isPenaltyGoalkeeperDiveChargeAllowed(player)
            FootballActionType.GK_DIVE -> isPenaltyGoalkeeperDiveExecutionAllowed(player)
            else -> false
        }

    fun isDefendingGoalkeeper(player: ServerPlayer): Boolean {
        if (!MatchParticipation.isParticipating(player)) return false
        if (MatchState.getPlayerTeam(player.uuid) != activeDefendingTeam) return false
        val official = when (activeDefendingTeam) {
            TeamSide.A -> PlayerRoleState.teamAGoalkeeper
            TeamSide.B -> PlayerRoleState.teamBGoalkeeper
        }
        if (player.uuid == official) return true
        return PlayerRoleState.isGoalkeeper(player)
    }

    fun onKickerTouchedBall(player: ServerPlayer, football: Football) {
        if (!isActive() || kickPhase != PenaltyKickPhase.AWAITING_KICK) return
        if (player.uuid != currentKickerUuid) return
        kickPhase = PenaltyKickPhase.RESOLVING
        resolveTicks = 0
        stationaryTicks = 0
        outcomeRecorded = false
        football.isImmovable = false
        football.immovableTargetPlayers = emptySet()
        SecondTouchTracker.begin(currentKickerTeam, player.uuid, SetPieceKind.PENALTY_KICK)
        val server = player.level().server
        FootballNetworking.broadcastPenaltyShootoutSync(server)
        FootballNetworking.broadcastSetPieceState(server)
    }

    fun onGoalLineCrossing(crossing: GoalCrossingUtil.GoalLineCrossing) {
        if (!isActive() || kickPhase != PenaltyKickPhase.RESOLVING || outcomeRecorded) return
        if (crossing.defendingTeam != penaltyGoalTeam) return
        if (crossing.inGoal && crossing.attackingTeam == currentKickerTeam) {
            applyOutcome(scored = true)
        } else if (!crossing.inGoal) {
            applyOutcome(scored = false)
        }
    }

    fun tick(server: MinecraftServer) {
        if (pendingAdvance) {
            pendingAdvance = false
            advanceAfterOutcome(server)
            return
        }
        if (!isActive()) return
        if (kickPhase == PenaltyKickPhase.SETUP && kickIntroTicksRemaining > 0) {
            kickIntroTicksRemaining--
            if (kickIntroTicksRemaining == 0) {
                kickPhase = PenaltyKickPhase.AWAITING_KICK
                releaseBallForKickerStrike(server)
                FootballNetworking.broadcastPenaltyShootoutSync(server)
                FootballNetworking.broadcastSetPieceState(server)
            }
            return
        }
        if (kickPhase != PenaltyKickPhase.RESOLVING || outcomeRecorded) return
        resolveTicks++
        if (resolveTicks >= RESOLVE_TIMEOUT_TICKS) {
            applyOutcome(scored = false)
            return
        }
        val football = findActiveFootball(server) ?: return
        if (football.simulationVelocity().lengthSqr() < STATIONARY_SPEED_SQR) {
            stationaryTicks++
            if (stationaryTicks >= STATIONARY_TICKS_NEEDED) {
                applyOutcome(scored = false)
            }
        } else {
            stationaryTicks = 0
        }
    }

    private fun findActiveFootball(server: MinecraftServer): Football? {
        if (activeFootballId < 0) return null
        for (level in server.getAllLevels()) {
            val entity = level.getEntity(activeFootballId)
            if (entity is Football) return entity
        }
        return null
    }

    /** 介绍期球体 [isImmovable]；进入 AWAITING_KICK 后须解除，否则主罚无法 [Football.kick]。 */
    private fun releaseBallForKickerStrike(server: MinecraftServer) {
        findActiveFootball(server)?.isImmovable = false
    }

    private fun applyOutcome(scored: Boolean) {
        if (outcomeRecorded || !active) return
        outcomeRecorded = true
        currentKickerUuid?.let { uuid ->
            kickCountByPlayer[uuid] = (kickCountByPlayer[uuid] ?: 0) + 1
            when (currentKickerTeam) {
                TeamSide.A -> kickedPlayersA.add(uuid)
                TeamSide.B -> kickedPlayersB.add(uuid)
            }
        }
        if (scored) {
            when (currentKickerTeam) {
                TeamSide.A -> penaltyScoreA++
                TeamSide.B -> penaltyScoreB++
            }
        }
        when (currentKickerTeam) {
            TeamSide.A -> kicksTakenA++
            TeamSide.B -> kicksTakenB++
        }
        totalKicksTaken++
        pendingAdvance = true
    }

    private fun advanceAfterOutcome(server: MinecraftServer) {
        FootballNetworking.broadcastPenaltyShootoutSync(server)
        val winner = checkWinner()
        if (winner != null) {
            finish(server, winner)
            return
        }
        if (totalKicksTaken >= REGULATION_KICKS_PER_TEAM * 2 && !suddenDeath &&
            penaltyScoreA == penaltyScoreB
        ) {
            suddenDeath = true
            server.playerList.broadcastSystemMessage(
                Component.translatable("match.penalty.sudden_death"),
                false,
            )
        }
        beginKick(server)
    }

    private fun teamForKickIndex(index: Int): TeamSide {
        return if (index % 2 == 0) firstKickTeam else firstKickTeam.opponent()
    }

    /** 点球大战开始时登记该队当时在线的 roster 队员。 */
    private fun captureEligibleKickPools(server: MinecraftServer) {
        eligibleKickersA.clear()
        eligibleKickersB.clear()
        for (uuid in MatchParticipation.filterParticipatingRoster(MatchState.teamAPlayers, server)) {
            if (server.playerList.getPlayer(uuid) != null) {
                eligibleKickersA.add(uuid)
            }
        }
        for (uuid in MatchParticipation.filterParticipatingRoster(MatchState.teamBPlayers, server)) {
            if (server.playerList.getPlayer(uuid) != null) {
                eligibleKickersB.add(uuid)
            }
        }
    }

    /**
     * 主罚选择：场上有非门将时绝不选门将（仅全队只剩门将在线时才由门将主罚）。
     * 开球时登记在 eligible 池中的队员须至少各踢一次后才允许任何人二踢。
     * 同优先级下优先被选次数较少的球员。
     */
    private fun pickKicker(team: TeamSide, server: MinecraftServer): UUID? {
        val roster = when (team) {
            TeamSide.A -> MatchState.teamAPlayers
            TeamSide.B -> MatchState.teamBPlayers
        }
        val eligible = when (team) {
            TeamSide.A -> eligibleKickersA
            TeamSide.B -> eligibleKickersB
        }
        val kicked = when (team) {
            TeamSide.A -> kickedPlayersA
            TeamSide.B -> kickedPlayersB
        }

        val present = roster.mapNotNull { server.playerList.getPlayer(it) }
            .filter { MatchParticipation.isParticipating(it) || isPenaltyWaitingSpectator(it) }
        if (present.isEmpty()) return null

        val officialGkUuid = when (team) {
            TeamSide.A -> PlayerRoleState.teamAGoalkeeper
            TeamSide.B -> PlayerRoleState.teamBGoalkeeper
        }
        fun kickCount(uuid: UUID) = kickCountByPlayer[uuid] ?: 0
        fun isOutfield(player: ServerPlayer): Boolean =
            officialGkUuid == null || player.uuid != officialGkUuid

        val outfieldPresent = present.filter { isOutfield(it) }
        val candidateBase = outfieldPresent.ifEmpty { present }

        val awaitingEligibleFirstKick = candidateBase.filter { player ->
            player.uuid in eligible && player.uuid !in kicked
        }
        val pool = awaitingEligibleFirstKick.ifEmpty { candidateBase }

        val minCount = pool.minOf { kickCount(it.uuid) }
        val candidates = pool.filter { kickCount(it.uuid) == minCount }
        return candidates.random().uuid
    }

    private fun beginKick(server: MinecraftServer) {
        val previousKickerUuid = currentKickerUuid
        val previousGkUuid = if (totalKicksTaken > 0) {
            resolveDefendingGoalkeeperUuid(server, activeDefendingTeam, previousKickerUuid)
        } else {
            null
        }
        previousKickerUuid?.let { uuid ->
            server.playerList.getPlayer(uuid)?.let { player ->
                PlayerRoleState.releasePenaltyKickOutfield(player)
                enterPenaltyWaitingSpectator(player, server)
            }
        }
        previousGkUuid?.takeIf { it != previousKickerUuid }?.let { uuid ->
            server.playerList.getPlayer(uuid)?.let { enterPenaltyWaitingSpectator(it, server) }
        }
        kickPhase = PenaltyKickPhase.SETUP
        outcomeRecorded = false
        currentKickerTeam = teamForKickIndex(totalKicksTaken)
        activeDefendingTeam = currentKickerTeam.opponent()
        currentKickerUuid = pickKicker(currentKickerTeam, server)
        currentKickerUuid?.let { uuid ->
            server.playerList.getPlayer(uuid)?.let { PlayerRoleState.enterPenaltyKickOutfield(it) }
        }
        val level = server.overworld()
        placeBallAndPlayers(level, server)
        val spot = defendingGoal().resolvedPenaltySpot()
        SetPieceState.begin(
            SetPieceContext(
                kind = SetPieceKind.PENALTY_KICK,
                restartTeam = currentKickerTeam,
                ballPos = Vec3(spot.x, spot.y, spot.z),
                defendingSide = penaltyGoalTeam,
            ),
        )
        SetPieceState.active?.let { SetPiecePlayerRepositioner.repositionInitialViolators(server, it) }
        kickIntroTicksRemaining = PenaltyShootoutTiming.KICK_INTRO_LOCK_TICKS
        MatchState.beginPenaltyKickWhistlePhase(currentKickerTeam)
        FootballNetworking.broadcastPenaltyShootoutSync(server)
        FootballNetworking.broadcastPenaltyKickStart(server)
        FootballNetworking.broadcastSetPieceState(server)
    }

    private fun placeBallAndPlayers(level: ServerLevel, server: MinecraftServer) {
        val goal = defendingGoal()
        val spot = goal.resolvedPenaltySpot()
        val ballPos = Vec3(spot.x, spot.y, spot.z)
        MatchState.resetFootball(level, ballPos)
        val box = net.minecraft.world.phys.AABB.ofSize(ballPos, 4.0, 4.0, 4.0)
        val football = level.getEntitiesOfClass(Football::class.java, box).minByOrNull { it.distanceToSqr(ballPos) }
        if (football != null) {
            activeFootballId = football.id
            football.isImmovable = true
            val immovable = mutableSetOf<UUID>()
            for (player in server.playerList.players) {
                if (player.uuid != currentKickerUuid && !isDefendingGoalkeeper(player)) {
                    immovable.add(player.uuid)
                }
            }
            football.immovableTargetPlayers = immovable
        } else {
            activeFootballId = -1
        }

        val towardGoal = goal.penaltyKickTowardGoal()
        val behindBall = goal.penaltyKickBehindBall()
        val kickerUuid = currentKickerUuid

        if (kickerUuid != null) {
            val kicker = server.playerList.getPlayer(kickerUuid)
            if (kicker != null) {
                activatePenaltyParticipant(kicker)
                val kx = spot.x + behindBall.x * KICKER_OFFSET_BLOCKS
                val kz = spot.z + behindBall.z * KICKER_OFFSET_BLOCKS
                val yaw = Math.toDegrees(kotlin.math.atan2(-towardGoal.x, towardGoal.z)).toFloat()
                kicker.teleportTo(level, kx, spot.y, kz, HashSet(), yaw, 0f, false)
            }
        }

        val gkUuid = resolveDefendingGoalkeeperUuid(server, activeDefendingTeam, kickerUuid)
        val gk = gkUuid?.let { server.playerList.getPlayer(it) }
        if (gk != null) {
            activatePenaltyParticipant(gk)
            val center = goal.goalCenter()
            val gkYaw = Math.toDegrees(kotlin.math.atan2(-behindBall.x, behindBall.z)).toFloat()
            gk.teleportTo(level, center.x, center.y, center.z, HashSet(), gkYaw, 0f, false)
        }

        placePenaltyWaitingSpectators(server, kickerUuid, gkUuid)
    }

    private fun penaltyWaitingPosition(): Vec3 {
        val spot = defendingGoal().resolvedPenaltySpot()
        return Vec3(spot.x, spot.y + WAITING_SPECTATOR_Y_OFFSET, spot.z)
    }

    private fun enterPenaltyWaitingSpectator(player: ServerPlayer, server: MinecraftServer) {
        if (player.uuid !in MatchState.teamAPlayers && player.uuid !in MatchState.teamBPlayers) return
        penaltyWaitingPreviousGameModes.putIfAbsent(player.uuid, player.gameMode.gameModeForPlayer)
        penaltyWaitingSpectators.add(player.uuid)
        player.setGameMode(GameType.SPECTATOR)
        val pos = penaltyWaitingPosition()
        val level = server.overworld()
        player.teleportTo(
            level,
            pos.x,
            pos.y,
            pos.z,
            HashSet(),
            player.yRot,
            player.xRot,
            false,
        )
    }

    private fun ensurePenaltyWaitingSpectator(player: ServerPlayer, server: MinecraftServer) {
        if (isPenaltyWaitingSpectator(player)) return
        enterPenaltyWaitingSpectator(player, server)
    }

    private fun activatePenaltyParticipant(player: ServerPlayer) {
        if (!isPenaltyWaitingSpectator(player)) return
        penaltyWaitingSpectators.remove(player.uuid)
        val previous = penaltyWaitingPreviousGameModes.remove(player.uuid) ?: GameType.ADVENTURE
        player.setGameMode(previous)
    }

    /** 其余队员旁观等待于点球点上方；已在等待区的玩家位置不变。 */
    private fun placePenaltyWaitingSpectators(
        server: MinecraftServer,
        kickerUuid: UUID?,
        defendingGkUuid: UUID?,
    ) {
        for (uuid in MatchState.teamAPlayers + MatchState.teamBPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            if (player.uuid == kickerUuid || player.uuid == defendingGkUuid) continue
            ensurePenaltyWaitingSpectator(player, server)
        }
    }

    private fun resolveDefendingGoalkeeperUuid(
        server: MinecraftServer,
        defendingTeam: TeamSide,
        excludingUuid: UUID?,
    ): UUID? {
        val official = when (defendingTeam) {
            TeamSide.A -> PlayerRoleState.teamAGoalkeeper
            TeamSide.B -> PlayerRoleState.teamBGoalkeeper
        }
        val officialPlayer = official?.let { server.playerList.getPlayer(it) }
            ?.takeIf { it.uuid != excludingUuid && MatchState.getPlayerTeam(it.uuid) == defendingTeam }
        if (officialPlayer != null) return officialPlayer.uuid
        return server.playerList.players.firstOrNull { player ->
            player.uuid != excludingUuid &&
                MatchState.getPlayerTeam(player.uuid) == defendingTeam &&
                PlayerRoleState.isGoalkeeper(player)
        }?.uuid
    }

    private fun restorePenaltyWaitingSpectator(player: ServerPlayer) {
        penaltyWaitingSpectators.remove(player.uuid)
        val previous = penaltyWaitingPreviousGameModes.remove(player.uuid) ?: return
        player.setGameMode(previous)
    }

    private fun restoreAllPenaltyWaitingSpectators(server: MinecraftServer?) {
        if (server == null) {
            penaltyWaitingSpectators.clear()
            penaltyWaitingPreviousGameModes.clear()
            return
        }
        for (uuid in (penaltyWaitingSpectators + penaltyWaitingPreviousGameModes.keys).toSet()) {
            server.playerList.getPlayer(uuid)?.let { restorePenaltyWaitingSpectator(it) }
        }
        penaltyWaitingSpectators.clear()
        penaltyWaitingPreviousGameModes.clear()
    }

    private fun checkWinner(): TeamSide? {
        val remainingA = (REGULATION_KICKS_PER_TEAM - kicksTakenA).coerceAtLeast(0)
        val remainingB = (REGULATION_KICKS_PER_TEAM - kicksTakenB).coerceAtLeast(0)
        if (!suddenDeath) {
            if (penaltyScoreA > penaltyScoreB + remainingB) return TeamSide.A
            if (penaltyScoreB > penaltyScoreA + remainingA) return TeamSide.B
            if (kicksTakenA >= REGULATION_KICKS_PER_TEAM && kicksTakenB >= REGULATION_KICKS_PER_TEAM) {
                if (penaltyScoreA != penaltyScoreB) {
                    return if (penaltyScoreA > penaltyScoreB) TeamSide.A else TeamSide.B
                }
            }
            return null
        }
        if (kicksTakenA > REGULATION_KICKS_PER_TEAM && kicksTakenB > REGULATION_KICKS_PER_TEAM &&
            kicksTakenA == kicksTakenB
        ) {
            if (penaltyScoreA != penaltyScoreB) {
                return if (penaltyScoreA > penaltyScoreB) TeamSide.A else TeamSide.B
            }
        }
        return null
    }

    private fun finish(server: MinecraftServer, winner: TeamSide) {
        PlayerRoleState.clearPenaltyKickOutfieldOverrides(server)
        restoreAllPenaltyWaitingSpectators(server)
        active = false
        lastWinner = winner
        kickPhase = PenaltyKickPhase.SETUP
        FootballSounds.playMatchWhistle(server, 2)
        FootballNetworking.broadcastPenaltyShootoutSync(server)
        MatchState.setPhase(MatchPhase.FINISHED, server)
        val nameA = MatchState.getTeamName(TeamSide.A).string
        val nameB = MatchState.getTeamName(TeamSide.B).string
        FootballNetworking.broadcastMatchResult(
            server,
            MatchState.teamAScore,
            MatchState.teamBScore,
            nameA,
            nameB,
            isDraw = false,
            wonByPenalties = true,
            penaltyScoreA = penaltyScoreA,
            penaltyScoreB = penaltyScoreB,
            penaltyWinner = winner,
        )
    }
}
