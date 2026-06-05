package net.astrorbits.football.input

import net.astrorbits.football.match.MatchFieldAreaUtil
import net.astrorbits.football.match.MatchPenaltyKickState
import net.astrorbits.football.match.MatchPhase
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.PenaltyShootoutState
import net.astrorbits.football.match.PlayerRoleState
import net.minecraft.server.level.ServerPlayer

/** 守门员专属场地操作（鱼跃、捡球、抛出、放下）的生效范围判定。 */
object GoalkeeperActionAccess {
    /** 比赛进行中仅守门员可用；比赛未开始（含准备/结算）时所有参赛球员可用。 */
    fun canUseGoalkeeperFieldActions(player: ServerPlayer): Boolean =
        PlayerRoleState.isGoalkeeper(player) || !MatchState.isDuringMatch()

    /**
     * 比赛期间守门员捡球 / 鱼跃扑救是否允许（须在己方大禁区内）。
     * 比赛未开始或赛前准备阶段无区域限制。
     */
    fun canUseDiveAndCatch(player: ServerPlayer): Boolean {
        if (!canUseGoalkeeperFieldActions(player)) {
            return false
        }
        if (!MatchState.isDuringMatch()) {
            return true
        }
        val team = MatchState.getPlayerTeam(player.uuid) ?: return false
        return MatchFieldAreaUtil.isPlayerInPenaltyArea(player, team)
    }

    /** 按住右键鱼跃蓄力（含点球大战开踢前的准备阶段）。 */
    fun canPrepareGoalkeeperDiveCharge(player: ServerPlayer): Boolean {
        if (!canUseGoalkeeperFieldActions(player)) {
            return false
        }
        if (!MatchState.isDuringMatch()) {
            return true
        }
        if (MatchState.currentPhase == MatchPhase.PENALTIES &&
            PenaltyShootoutState.isPenaltyGoalkeeperDiveChargeAllowed(player)
        ) {
            return true
        }
        if (MatchPenaltyKickState.isPenaltyGoalkeeperDiveChargeAllowed(player)) {
            return true
        }
        return canUseDiveAndCatch(player)
    }

    /** 释放右键执行鱼跃扑救。 */
    fun canExecuteGoalkeeperDive(player: ServerPlayer): Boolean {
        if (!canUseGoalkeeperFieldActions(player)) {
            return false
        }
        if (!MatchState.isDuringMatch()) {
            return true
        }
        if (MatchState.currentPhase == MatchPhase.PENALTIES &&
            PenaltyShootoutState.isPenaltyGoalkeeperDiveExecutionAllowed(player)
        ) {
            return true
        }
        if (MatchPenaltyKickState.isPenaltyGoalkeeperDiveExecutionAllowed(player)) {
            return true
        }
        return canUseDiveAndCatch(player)
    }
}
