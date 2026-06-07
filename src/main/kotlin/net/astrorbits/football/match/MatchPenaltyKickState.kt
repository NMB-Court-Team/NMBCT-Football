package net.astrorbits.football.match

import net.astrorbits.football.Football
import net.astrorbits.football.FootballParticles
import net.astrorbits.football.network.FootballActionType
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.util.GoalCrossingUtil
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.*

/** 正赛单次点球（非点球大战）。 */
object MatchPenaltyKickState {
    private const val RESOLVE_TIMEOUT_TICKS = 600
    private const val STATIONARY_SPEED_SQR = 0.002
    private const val STATIONARY_TICKS_NEEDED = 20
    private const val KICKER_OFFSET_BLOCKS = 2.5

    var active: Boolean = false
        private set
    var defendingTeam: TeamSide = TeamSide.A
        private set
    var kickingTeam: TeamSide = TeamSide.A
        private set
    var currentKickerUuid: UUID? = null
        private set
    var kickPhase: PenaltyKickPhase = PenaltyKickPhase.SETUP
        private set

    private var resolveTicks = 0
    private var stationaryTicks = 0
    private var outcomeRecorded = false
    private var activeFootballId: Int = -1
    private var kickIntroTicksRemaining = 0
    private var lastServer: MinecraftServer? = null

    fun isActive(): Boolean = active && MatchState.currentPhase != MatchPhase.PENALTIES

    fun isResolving(): Boolean = isActive() && kickPhase == PenaltyKickPhase.RESOLVING

    fun defendingGoal(): GoalConfig {
        val config = MatchConfigHolder.current
        return when (defendingTeam) {
            TeamSide.A -> config.goalA
            TeamSide.B -> config.goalB
        }
    }

    fun begin(server: MinecraftServer, level: ServerLevel, pending: PendingAfterReset.MatchPenaltyKick) {
        clear(server)
        lastServer = server
        active = true
        defendingTeam = pending.defendingTeam
        kickingTeam = pending.kickoffTeam
        currentKickerUuid = resolveKicker(kickingTeam, pending.preferredKickerUuid, server)
        kickPhase = PenaltyKickPhase.SETUP
        outcomeRecorded = false
        MatchState.postGoalResetPending = false

        placeBallAndPlayers(level, server)
        val spot = defendingGoal().resolvedPenaltySpot()
        SetPieceState.begin(
            SetPieceContext(
                kind = SetPieceKind.PENALTY_KICK,
                restartTeam = kickingTeam,
                ballPos = Vec3(spot.x, spot.y, spot.z),
                defendingSide = defendingTeam,
            ),
        )
        SetPieceState.active?.let { SetPiecePlayerRepositioner.repositionInitialViolators(server, it) }
        kickIntroTicksRemaining = PenaltyShootoutTiming.KICK_INTRO_LOCK_TICKS
        MatchState.beginPenaltyKickWhistlePhase(kickingTeam)
        FootballNetworking.broadcastSetPieceState(server)
    }

    fun clear(server: MinecraftServer? = null) {
        if (active && MatchState.kickoffTeam == kickingTeam && !MatchState.kickoffTouched) {
            MatchState.clearKickoffWhistleTimers()
            MatchState.kickoffTeam = null
        }
        active = false
        currentKickerUuid = null
        kickPhase = PenaltyKickPhase.SETUP
        PlayerRoleState.clearPenaltyKickOutfieldOverrides(server)
        if (SetPieceState.active?.kind == SetPieceKind.PENALTY_KICK && !PenaltyShootoutState.isActive()) {
            SetPieceState.clear()
            server?.let { FootballNetworking.broadcastSetPieceState(it) }
        }
        resolveTicks = 0
        stationaryTicks = 0
        outcomeRecorded = false
        activeFootballId = -1
        kickIntroTicksRemaining = 0
        lastServer = null
    }

    fun isFootballInteractionAllowed(player: ServerPlayer): Boolean {
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
        if (kickPhase != PenaltyKickPhase.AWAITING_KICK) return true
        return when (action) {
            FootballActionType.PASS,
            FootballActionType.SHOOT,
            -> false
            else -> true
        }
    }

    fun isMovementRestricted(player: ServerPlayer): Boolean {
        if (!MatchParticipation.isParticipating(player)) return false
        if (!isActive()) return false
        if (player.uuid == currentKickerUuid) return false
        if (isDefendingGoalkeeper(player)) return false
        return true
    }

    fun isPenaltyGoalkeeperDiveChargeAllowed(player: ServerPlayer): Boolean {
        if (!isActive() || !isDefendingGoalkeeper(player)) return false
        return kickPhase == PenaltyKickPhase.SETUP || kickPhase == PenaltyKickPhase.AWAITING_KICK
    }

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
        if (MatchState.getPlayerTeam(player.uuid) != defendingTeam) return false
        val official = when (defendingTeam) {
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
        SecondTouchTracker.begin(kickingTeam, player.uuid, SetPieceKind.PENALTY_KICK)
        lastServer?.let { FootballNetworking.broadcastSetPieceState(it) }
    }

    fun onGoalLineCrossing(crossing: GoalCrossingUtil.GoalLineCrossing) {
        if (!isResolving() || outcomeRecorded) return
        if (crossing.defendingTeam != defendingTeam) return
        if (crossing.inGoal && crossing.attackingTeam == kickingTeam) {
            applyOutcome(scored = true)
        } else if (!crossing.inGoal) {
            applyOutcome(scored = false)
        }
    }

    fun tick(server: MinecraftServer) {
        if (!isActive()) return
        lastServer = server
        if (kickPhase == PenaltyKickPhase.SETUP && kickIntroTicksRemaining > 0) {
            kickIntroTicksRemaining--
            if (kickIntroTicksRemaining == 0) {
                kickPhase = PenaltyKickPhase.AWAITING_KICK
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

    private fun applyOutcome(scored: Boolean) {
        if (outcomeRecorded || !active) return
        outcomeRecorded = true
        val server = lastServer ?: return
        val level = server.overworld()
        if (scored) {
            finishScored(server, level)
        } else {
            finishOpenPlay(server)
        }
    }

    private fun finishScored(server: MinecraftServer, level: ServerLevel) {
        val kickerUuid = currentKickerUuid
        val scorerName = kickerUuid?.let { server.playerList.getPlayer(it)?.gameProfile?.name } ?: "?"
        val scorerTeam = kickerUuid?.let { MatchState.getPlayerTeam(it) } ?: kickingTeam
        MatchState.clearDirectGoalRestriction()
        MatchState.clearPendingOffsideSnapshot()
        MatchState.postGoalResetPending = true
        MatchState.onGoal(kickingTeam)
        FootballNetworking.broadcastGoalScored(
            server,
            kickingTeam,
            scorerName,
            scorerTeam,
            MatchState.teamAScore,
            MatchState.teamBScore,
            ownGoal = scorerTeam != kickingTeam,
        )
        val football = findActiveFootball(server)
        if (football != null) {
            FootballParticles.playGoal(level, FootballParticles.centerOfFootball(football))
        }
        clear(server)
        PostGoalBallResetScheduler.schedule(
            level,
            afterReset = PendingAfterReset.PostGoal(defendingTeam),
        )
    }

    private fun finishOpenPlay(server: MinecraftServer) {
        clear(server)
    }

    private fun resolveKicker(team: TeamSide, preferred: UUID?, server: MinecraftServer): UUID? {
        preferred?.let { uuid ->
            val player = server.playerList.getPlayer(uuid)
            if (player != null &&
                MatchState.getPlayerTeam(uuid) == team &&
                !PlayerRoleState.isDesignatedGoalkeeper(player)
            ) {
                return uuid
            }
        }
        val outfield = server.playerList.players.filter { player ->
            MatchState.getPlayerTeam(player.uuid) == team &&
                MatchParticipation.isParticipating(player) &&
                !PlayerRoleState.isDesignatedGoalkeeper(player)
        }
        return outfield.randomOrNull()?.uuid
    }

    private fun placeBallAndPlayers(level: ServerLevel, server: MinecraftServer) {
        val goal = defendingGoal()
        val spot = goal.resolvedPenaltySpot()
        val ballPos = Vec3(spot.x, spot.y, spot.z)
        MatchState.resetFootball(level, ballPos)
        val box = AABB.ofSize(ballPos, 4.0, 4.0, 4.0)
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
                val kx = spot.x + behindBall.x * KICKER_OFFSET_BLOCKS
                val kz = spot.z + behindBall.z * KICKER_OFFSET_BLOCKS
                val yaw = Math.toDegrees(kotlin.math.atan2(-towardGoal.x, towardGoal.z)).toFloat()
                kicker.teleportTo(level, kx, spot.y, kz, HashSet(), yaw, 0f, false)
                PlayerRoleState.enterPenaltyKickOutfield(kicker)
            }
        }

        val gkUuid = when (defendingTeam) {
            TeamSide.A -> PlayerRoleState.teamAGoalkeeper
            TeamSide.B -> PlayerRoleState.teamBGoalkeeper
        }
        val gk = gkUuid?.let { server.playerList.getPlayer(it) }
            ?: server.playerList.players.firstOrNull { isDefendingGoalkeeper(it) }
        if (gk != null) {
            val center = goal.goalCenter()
            val gkYaw = Math.toDegrees(kotlin.math.atan2(-behindBall.x, behindBall.z)).toFloat()
            gk.teleportTo(level, center.x, center.y, center.z, HashSet(), gkYaw, 0f, false)
        }

        val waitPos = MatchConfigHolder.current.kickOff.toVec3()
        for (player in server.playerList.players) {
            if (player.uuid == kickerUuid) continue
            if (gk != null && player.uuid == gk.uuid) continue
            if (!MatchParticipation.isParticipating(player)) continue
            val team = MatchState.getPlayerTeam(player.uuid) ?: continue
            if (team == TeamSide.A || team == TeamSide.B) {
                player.teleportTo(level, waitPos.x, waitPos.y, waitPos.z, HashSet(), player.yRot, player.xRot, false)
            }
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
}
