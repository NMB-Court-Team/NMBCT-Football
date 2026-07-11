package net.astrorbits.football.match

import net.astrorbits.football.Football
import net.astrorbits.football.util.GoalkeeperUtil
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

/**
 * 守门员在禁区内用手触球时，若足球本身位于己方大禁区外，判对方直接任意球。
 */
object GoalkeeperCatchFoulDetector {
    fun tryAwardCatchOutsidePenaltyArea(player: ServerPlayer, football: Football): Boolean {
        if (!MatchState.isDuringMatch()) return false
        if (MatchState.postGoalResetPending) return false
        if (PenaltyShootoutState.isActive() || MatchPenaltyKickState.isActive()) return false
        if (ThrowInSetPieceFlow.isMovementFrozen(player)) return false
        if (!PlayerRoleState.isGoalkeeper(player)) return false
        val team = MatchState.getPlayerTeam(player.uuid) ?: return false
        val center = GoalkeeperUtil.ballCenter(football)
        if (MatchFieldAreaUtil.isInPenaltyArea(MatchConfigHolder.current, team, center.x, center.z)) {
            return false
        }
        val foulPos = Vec3(football.x, football.y, football.z)
        football.releaseHold()
        return FreeKickAwards.awardDirectFreeKick(
            player.level(),
            foulPos,
            team,
            player.uuid,
            FreeKickFoulReason.GOALKEEPER_LEFT_PENALTY_AREA,
        )
    }
}
