package net.astrorbits.football.match

import net.astrorbits.football.Football
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.util.GoalCrossingUtil
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.UUID
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

    var active: Boolean = false
        private set
    var penaltyScoreA: Int = 0
        private set
    var penaltyScoreB: Int = 0
        private set
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
    private var resolveTicks = 0
    private var stationaryTicks = 0
    private var outcomeRecorded = false
    private var activeFootballId: Int = -1
    private var pendingAdvance = false
    private var kickIntroTicksRemaining = 0

    fun isActive(): Boolean = active && MatchState.currentPhase == MatchPhase.PENALTIES

    fun start(server: MinecraftServer) {
        clear()
        active = true
        lastWinner = null
        activeDefendingTeam = TeamSide.entries[Random.nextInt(TeamSide.entries.size)]
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
        beginKick(server)
    }

    fun clear(server: MinecraftServer? = null) {
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
        PlayerRoleState.clearPenaltyKickOutfieldOverrides(server)
        resolveTicks = 0
        stationaryTicks = 0
        outcomeRecorded = false
        activeFootballId = -1
        pendingAdvance = false
        kickIntroTicksRemaining = 0
    }

    fun defendingGoal(): GoalConfig {
        val config = MatchConfigHolder.current
        return when (activeDefendingTeam) {
            TeamSide.A -> config.goalA
            TeamSide.B -> config.goalB
        }
    }

    fun isPenaltyFootballInteractionAllowed(player: ServerPlayer): Boolean {
        if (!isActive()) return true
        if (kickPhase == PenaltyKickPhase.SETUP) return false
        if (player.uuid == currentKickerUuid) return true
        if (isDefendingGoalkeeper(player)) return true
        return false
    }

    fun isPenaltyMovementRestricted(player: ServerPlayer): Boolean {
        if (!MatchParticipation.isParticipating(player)) return false
        if (!isActive()) return false
        if (kickPhase == PenaltyKickPhase.SETUP) return true
        if (player.uuid == currentKickerUuid) return false
        if (isDefendingGoalkeeper(player)) return false
        return true
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
    }

    fun onGoalLineCrossing(crossing: GoalCrossingUtil.GoalLineCrossing) {
        if (!isActive() || kickPhase != PenaltyKickPhase.RESOLVING || outcomeRecorded) return
        if (crossing.defendingTeam != activeDefendingTeam) return
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
                FootballNetworking.broadcastPenaltyShootoutSync(server)
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

    private fun applyOutcome(scored: Boolean) {
        if (outcomeRecorded || !active) return
        outcomeRecorded = true
        currentKickerUuid?.let { uuid ->
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
     * 主罚选择：优先尚未踢过的在场 eligible 队员；仅当所有**当前仍在场**的 eligible 都已踢过至少一次后，才允许二踢。
     * 开球时在线、之后离场的队员不阻塞二踢。
     */
    private fun pickKicker(team: TeamSide, server: MinecraftServer): UUID? {
        val eligible = when (team) {
            TeamSide.A -> eligibleKickersA
            TeamSide.B -> eligibleKickersB
        }
        val kicked = when (team) {
            TeamSide.A -> kickedPlayersA
            TeamSide.B -> kickedPlayersB
        }
        if (eligible.isEmpty()) return null

        val present = eligible.mapNotNull { server.playerList.getPlayer(it) }
        if (present.isEmpty()) return null

        val outfield = present.filter { !PlayerRoleState.isDesignatedGoalkeeper(it) }
        if (outfield.isNotEmpty()) {
            pickKickerFromPool(outfield, kicked)?.let { return it }
        }
        return pickKickerFromPool(present, kicked)
    }

    private fun pickKickerFromPool(
        candidates: List<ServerPlayer>,
        kicked: Set<UUID>,
    ): UUID? {
        if (candidates.isEmpty()) return null
        val awaitingFirstKick = candidates.filter { it.uuid !in kicked }
        if (awaitingFirstKick.isNotEmpty()) {
            return awaitingFirstKick.random().uuid
        }
        return candidates.random().uuid
    }

    private fun beginKick(server: MinecraftServer) {
        currentKickerUuid?.let { uuid ->
            server.playerList.getPlayer(uuid)?.let { PlayerRoleState.releasePenaltyKickOutfield(it) }
        }
        kickPhase = PenaltyKickPhase.SETUP
        outcomeRecorded = false
        currentKickerTeam = teamForKickIndex(totalKicksTaken)
        currentKickerUuid = pickKicker(currentKickerTeam, server)
        currentKickerUuid?.let { uuid ->
            server.playerList.getPlayer(uuid)?.let { PlayerRoleState.enterPenaltyKickOutfield(it) }
        }
        val level = server.overworld()
        placeBallAndPlayers(level, server)
        kickIntroTicksRemaining = PenaltyShootoutTiming.KICK_INTRO_LOCK_TICKS
        FootballNetworking.broadcastPenaltyShootoutSync(server)
        FootballNetworking.broadcastPenaltyKickStart(server)
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

        val facing = goal.goalLineFacing()
        val towardGoal = facing.scale(-1.0)
        val kickerUuid = currentKickerUuid
        if (kickerUuid != null) {
            val kicker = server.playerList.getPlayer(kickerUuid)
            if (kicker != null) {
                val kx = spot.x + towardGoal.x * KICKER_OFFSET_BLOCKS
                val kz = spot.z + towardGoal.z * KICKER_OFFSET_BLOCKS
                val yaw = Math.toDegrees(kotlin.math.atan2(-towardGoal.x, towardGoal.z)).toFloat()
                kicker.teleportTo(level, kx, spot.y, kz, java.util.HashSet(), yaw, 0f, false)
            }
        }

        val gkUuid = when (activeDefendingTeam) {
            TeamSide.A -> PlayerRoleState.teamAGoalkeeper
            TeamSide.B -> PlayerRoleState.teamBGoalkeeper
        }
        val gk = gkUuid?.let { server.playerList.getPlayer(it) }
            ?: server.playerList.players.firstOrNull { isDefendingGoalkeeper(it) }
        if (gk != null) {
            val center = goal.goalCenter()
            val gkYaw = Math.toDegrees(kotlin.math.atan2(facing.x, -facing.z)).toFloat()
            gk.teleportTo(level, center.x, center.y, center.z, java.util.HashSet(), gkYaw, 0f, false)
        }

        val waitPos = MatchConfigHolder.current.kickOff.toVec3()
        for (player in server.playerList.players) {
            if (player.uuid == kickerUuid) continue
            if (gk != null && player.uuid == gk.uuid) continue
            if (!MatchParticipation.isParticipating(player)) continue
            val team = MatchState.getPlayerTeam(player.uuid) ?: continue
            if (team == TeamSide.A || team == TeamSide.B) {
                player.teleportTo(level, waitPos.x, waitPos.y, waitPos.z, java.util.HashSet(), player.yRot, player.xRot, false)
            }
        }
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
