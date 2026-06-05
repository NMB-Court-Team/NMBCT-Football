package net.astrorbits.football.input

import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.PlayerRoleState
import net.astrorbits.football.util.GoalkeeperUtil
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer

/** 比赛期间禁止守门员持球离开己方大禁区。 */
object GoalkeeperPenaltyAreaHoldGuard {
    private val LEAVE_PENALTY_AREA_MESSAGE = Component.translatable("match.gk.penalty_area_hold_forbidden")
        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    private fun tick(server: MinecraftServer) {
        if (!MatchState.isDuringMatch()) {
            return
        }

        for (player in server.playerList.players) {
            if (!PlayerRoleState.isGoalkeeper(player)) {
                continue
            }
            if (GoalkeeperActionAccess.canUseDiveAndCatch(player)) {
                continue
            }
            val football = GoalkeeperUtil.findHeldFootball(player) ?: continue
            football.forceDropAt(player)
            player.sendSystemMessage(LEAVE_PENALTY_AREA_MESSAGE)
        }
    }
}
