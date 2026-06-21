package net.astrorbits.football.match

import net.astrorbits.football.Football
import net.astrorbits.football.FootballParticles
import net.astrorbits.football.network.FootballNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * 禁区内滑铲犯规后：吹哨并锁定全员，等待在途足球自然出结果（同正赛点球 [MatchPenaltyKickState] RESOLVING）。
 * 进犯规方球门 → 有效进球；否则 → 正赛点球。
 */
object PenaltyFoulGoalWatchState {
    private const val RESOLVE_TIMEOUT_TICKS = 600
    private const val STATIONARY_SPEED_SQR = 0.002
    private const val STATIONARY_TICKS_NEEDED = 20
    private val ALL_FOOTBALLS_AABB = AABB(Vec3(-3.0E7, -3.0E7, -3.0E7), Vec3(3.0E7, 3.0E7, 3.0E7))

    var kickingTeam: TeamSide = TeamSide.A
        private set
    var defendingTeam: TeamSide = TeamSide.A
        private set

    private var pendingPenalty: PendingAfterReset.MatchPenaltyKick? = null
    private var resolveTicks = 0
    private var stationaryTicks = 0
    private var outcomeRecorded = false
    private var activeFootballId = -1
    private var lastServer: MinecraftServer? = null

    fun isActive(): Boolean = pendingPenalty != null && !outcomeRecorded

    fun begin(server: MinecraftServer, level: ServerLevel, pending: PendingAfterReset.MatchPenaltyKick, football: Football?) {
        clear()
        lastServer = server
        pendingPenalty = pending
        kickingTeam = pending.kickoffTeam
        defendingTeam = pending.defendingTeam
        activeFootballId = football?.id ?: -1
        outcomeRecorded = false
        resolveTicks = 0
        stationaryTicks = 0
    }

    fun clear() {
        pendingPenalty = null
        outcomeRecorded = false
        resolveTicks = 0
        stationaryTicks = 0
        activeFootballId = -1
        lastServer = null
    }

    fun onGoalLineCrossing(crossing: net.astrorbits.football.util.GoalCrossingUtil.GoalLineCrossing) {
        if (!isActive() || outcomeRecorded) return
        if (crossing.defendingTeam != defendingTeam) return
        if (crossing.inGoal && crossing.attackingTeam == kickingTeam) {
            finishScored()
        } else if (!crossing.inGoal) {
            finishMissed()
        }
    }

    fun onSidelineOut() {
        if (!isActive() || outcomeRecorded) return
        finishMissed()
    }

    fun tick(server: MinecraftServer) {
        if (!isActive()) return
        lastServer = server
        resolveTicks++
        if (resolveTicks >= RESOLVE_TIMEOUT_TICKS) {
            finishMissed()
            return
        }
        val football = findActiveFootball(server) ?: return
        if (football.simulationVelocity().lengthSqr() < STATIONARY_SPEED_SQR) {
            stationaryTicks++
            if (stationaryTicks >= STATIONARY_TICKS_NEEDED) {
                finishMissed()
            }
        } else {
            stationaryTicks = 0
        }
    }

    private fun finishScored() {
        if (outcomeRecorded || pendingPenalty == null) return
        outcomeRecorded = true
        val server = lastServer ?: return
        val level = server.overworld()
        val football = findActiveFootball(server)
        val scorerUuid = football?.goalAttributionPlayer ?: football?.lastPhysicalTouch
        val scorerName = scorerUuid?.let { server.playerList.getPlayer(it)?.gameProfile?.name } ?: "?"
        val scorerTeam = scorerUuid?.let { MatchState.getPlayerTeam(it) } ?: kickingTeam
        MatchState.clearDirectGoalRestriction()
        MatchState.clearPendingOffsideSnapshot()
        MatchState.clearPendingGoalLineOut()
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
        if (football != null) {
            FootballParticles.playGoal(level, FootballParticles.centerOfFootball(football))
        }
        clear()
        PostGoalBallResetScheduler.schedule(
            level,
            afterReset = PendingAfterReset.PostGoal(defendingTeam),
        )
    }

    private fun finishMissed() {
        if (outcomeRecorded) return
        val pending = pendingPenalty ?: return
        outcomeRecorded = true
        val server = lastServer ?: return
        val level = server.overworld()
        val goal = MatchFieldAreaUtil.goalForSide(MatchConfigHolder.current, pending.defendingTeam)
        val spot = goal.resolvedPenaltySpot()
        val ballPos = Vec3(spot.x, spot.y, spot.z)
        clear()
        MatchState.postGoalResetPending = true
        PostGoalBallResetScheduler.schedule(level, ballPos, pending)
        FootballNetworking.broadcastBallResetPending(server, pending.kickoffTeam, SetPieceKind.PENALTY_KICK)
    }

    private fun findActiveFootball(server: MinecraftServer): Football? {
        if (activeFootballId >= 0) {
            for (level in server.getAllLevels()) {
                val entity = level.getEntity(activeFootballId)
                if (entity is Football) return entity
            }
        }
        return server.overworld().getEntitiesOfClass(Football::class.java, ALL_FOOTBALLS_AABB).firstOrNull()
    }
}
