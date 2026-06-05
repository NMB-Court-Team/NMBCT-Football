package net.astrorbits.football.input

import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.PlayerRoleState
import net.minecraft.server.level.ServerPlayer

/** 守门员专属场地操作（鱼跃、捡球、抛出、放下）的生效范围判定。 */
object GoalkeeperActionAccess {
    /** 比赛进行中仅守门员可用；比赛未开始（含准备/结算）时所有参赛球员可用。 */
    fun canUseGoalkeeperFieldActions(player: ServerPlayer): Boolean =
        PlayerRoleState.isGoalkeeper(player) || !MatchState.isDuringMatch()
}
