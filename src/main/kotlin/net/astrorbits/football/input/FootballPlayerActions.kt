package net.astrorbits.football.input

import net.astrorbits.football.network.FootballActionC2SPayload
import net.astrorbits.football.network.FootballActionType
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.util.FootballKickUtil
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object FootballPlayerActions {
    private val lastActionTick = ConcurrentHashMap<UUID, Long>()

    fun handle(player: ServerPlayer, payload: FootballActionC2SPayload) {
        when (payload.action) {
            FootballActionType.DRIBBLE_HOLD -> handleDribbleHold(player)
            FootballActionType.DRIBBLE_END -> FootballDribbleSessions.end(player)
            else -> handleKickAction(player, payload)
        }
    }

    private fun handleDribbleHold(player: ServerPlayer) {
        if (!canAct(player)) {
            FootballDribbleSessions.end(player)
            return
        }

        val football = FootballKickUtil.findNearestFootball(player, FootballInputConfig.PLAYER_KICK_RANGE)
        if (football == null) {
            FootballDribbleSessions.end(player)
            return
        }

        if (player.distanceToSqr(football) > FootballInputConfig.PLAYER_KICK_RANGE * FootballInputConfig.PLAYER_KICK_RANGE) {
            FootballDribbleSessions.end(player)
            return
        }

        if (!FootballMovementInputUtil.hasMovementInput(player)) {
            FootballDribbleSessions.end(player)
            return
        }

        val now = player.level().gameTime
        val isNewSession = FootballDribbleSessions.beginOrRefresh(player, football, now)
        if (isNewSession) {
            FootballSounds.playDribble(player)
        }
    }

    private fun handleKickAction(player: ServerPlayer, payload: FootballActionC2SPayload) {
        FootballDribbleSessions.end(player)

        if (!canAct(player)) {
            return
        }

        val football = FootballKickUtil.findNearestFootball(player, FootballInputConfig.PLAYER_KICK_RANGE) ?: return

        if (player.distanceToSqr(football) > FootballInputConfig.PLAYER_KICK_RANGE * FootballInputConfig.PLAYER_KICK_RANGE) {
            return
        }

        val now = player.level().gameTime
        val last = lastActionTick[player.uuid] ?: -1
        if (now - last < FootballInputConfig.ACTION_COOLDOWN_TICKS) {
            return
        }

        val sprinting = payload.flags and FootballInputConfig.FLAG_SPRINT != 0

        when (payload.action) {
            FootballActionType.PASS -> {
                FootballKickUtil.applyKickToFootball(player, football, FootballKickUtil.resolvePassParams())
                FootballSounds.playKick(player)
                lastActionTick[player.uuid] = now
            }
            FootballActionType.SHOOT -> {
                FootballKickUtil.applyKickToFootball(
                    player,
                    football,
                    FootballKickUtil.resolveShootParams(payload.chargeRatio, sprinting)
                )
                FootballSounds.playKick(player)
                lastActionTick[player.uuid] = now
            }
            FootballActionType.TRAP -> {
                football.trap()
                FootballSounds.playTrap(player)
                lastActionTick[player.uuid] = now
            }
            FootballActionType.CHIP -> {
                FootballKickUtil.applyKickToFootball(
                    player,
                    football,
                    FootballKickUtil.resolveChipParams(player)
                )
                FootballSounds.playKick(player)
                lastActionTick[player.uuid] = now
            }
            FootballActionType.DRIBBLE_HOLD, FootballActionType.DRIBBLE_END -> Unit
        }
    }

    private fun canAct(player: Player): Boolean = player.mainHandItem.isEmpty
}
