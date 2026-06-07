package net.astrorbits.football.match

import net.astrorbits.football.network.FootballNetworking
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import java.util.*

/**
 * 比赛中任意球判罚（内部 API，供犯规检测等模块调用）。
 *
 * @param foulingTeam 犯下犯规、被判罚的球队
 * @param foulingPlayerUuid 犯规球员，须属于 [foulingTeam]
 * @param foulReason 犯规原因（用于 HUD 与广播）
 */
object FreeKickAwards {
    /** 直接任意球：可射门直接得分。 */
    fun awardDirectFreeKick(
        level: ServerLevel,
        ballPos: Vec3,
        foulingTeam: TeamSide,
        foulingPlayerUuid: UUID,
        foulReason: FreeKickFoulReason,
    ): Boolean = award(level, ballPos, foulingTeam, foulingPlayerUuid, FreeKickType.DIRECT, foulReason)

    /**
     * 间接任意球：须先由其他球员触球后才可得分；直接射门无效，按掷界外球规则判角球/球门球。
     */
    fun awardIndirectFreeKick(
        level: ServerLevel,
        ballPos: Vec3,
        foulingTeam: TeamSide,
        foulingPlayerUuid: UUID,
        foulReason: FreeKickFoulReason,
    ): Boolean = award(level, ballPos, foulingTeam, foulingPlayerUuid, FreeKickType.INDIRECT, foulReason)

    private fun award(
        level: ServerLevel,
        ballPos: Vec3,
        foulingTeam: TeamSide,
        foulingPlayerUuid: UUID,
        type: FreeKickType,
        foulReason: FreeKickFoulReason,
    ): Boolean {
        if (!MatchState.isDuringMatch()) return false
        if (MatchState.postGoalResetPending) return false
        if (MatchState.getPlayerTeam(foulingPlayerUuid) != foulingTeam) return false

        val config = MatchConfigHolder.current
        val awardedTeam = foulingTeam.opponent()
        val server = level.server ?: return false

        MatchState.clearPendingGoalLineOut()
        MatchState.clearDirectGoalRestriction()
        MatchState.clearPendingOffsideSnapshot()
        SecondTouchTracker.clear()
        SetPieceState.clear()
        GoalKickSetPieceFlow.clear(server)
        ThrowInSetPieceFlow.clear(server)
        FreeKickSetPieceFlow.clear(server)
        SetPieceAreaViolationMonitor.clearAll(server)
        MatchState.postGoalResetPending = true

        if (type == FreeKickType.DIRECT &&
            MatchFieldAreaUtil.isInPenaltyArea(config, foulingTeam, ballPos.x, ballPos.z)
        ) {
            val goal = MatchFieldAreaUtil.goalForSide(config, foulingTeam)
            val spot = goal.resolvedPenaltySpot()
            val penaltyBallPos = Vec3(spot.x, spot.y, spot.z)
            PostGoalBallResetScheduler.schedule(
                level,
                penaltyBallPos,
                PendingAfterReset.MatchPenaltyKick(
                    kickoffTeam = awardedTeam,
                    defendingTeam = foulingTeam,
                    preferredKickerUuid = null,
                    lastTouchPlayerUuid = foulingPlayerUuid,
                ),
            )
            FootballNetworking.broadcastFreeKickAward(
                server,
                FreeKickType.PENALTY,
                foulReason,
                foulingTeam,
                awardedTeam,
                foulingPlayerUuid,
                penaltyBallPos.x,
                penaltyBallPos.y,
                penaltyBallPos.z,
            )
            return true
        }

        PostGoalBallResetScheduler.schedule(
            level,
            ballPos,
            PendingAfterReset.FreeKick(
                kickoffTeam = awardedTeam,
                ballPos = ballPos,
                freeKickType = type,
                preferredTakerUuid = null,
                lastTouchPlayerUuid = foulingPlayerUuid,
                foulPos = ballPos,
            ),
        )

        FootballNetworking.broadcastFreeKickAward(
            server,
            type,
            foulReason,
            foulingTeam,
            awardedTeam,
            foulingPlayerUuid,
            ballPos.x,
            ballPos.y,
            ballPos.z,
        )
        return true
    }
}
