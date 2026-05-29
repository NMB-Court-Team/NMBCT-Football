package net.astrorbits.football.input

import net.astrorbits.football.match.PlayerRoleState
import net.astrorbits.football.network.FootballActionC2SPayload
import net.astrorbits.football.network.FootballActionType
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.FootballParticles
import net.astrorbits.football.item.FootballItem
import net.astrorbits.football.util.FootballKickUtil
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object FootballPlayerActions {
    private val lastActionTick = ConcurrentHashMap<UUID, Long>()

    fun handle(player: ServerPlayer, payload: FootballActionC2SPayload) {
        if (payload.action == FootballActionType.ITEM_THROW) {
            FootballItem.tryThrowFromMainHand(player)
            return
        }
        if (PlayerRoleState.isGoalkeeper(player)) {
            GoalkeeperActions.handle(player, payload)
            return
        }

        when (payload.action) {
            FootballActionType.DRIBBLE_HOLD -> handleDribbleHold(player)
            FootballActionType.DRIBBLE_END -> FootballDribbleSessions.end(player)
            else -> handleKickAction(player, payload)
        }
    }

    private fun handleDribbleHold(player: ServerPlayer) {
        if (PlayerRoleState.isGoalkeeper(player)) {
            FootballDribbleSessions.end(player)
            return
        }

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
        FootballDribbleSessions.beginOrRefresh(player, football, now)
    }

    private fun handleKickAction(player: ServerPlayer, payload: FootballActionC2SPayload) {
        FootballDribbleSessions.end(player)

        if (!canAct(player)) {
            return
        }

        val football = FootballKickUtil.findNearestFootball(player, FootballInputConfig.PLAYER_KICK_RANGE) ?: return

        if (football.isHeld()) {
            return
        }

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
                val params = FootballKickUtil.resolvePassParams()
                FootballKickUtil.applyKickToFootball(player, football, params)
                FootballSounds.playKick(player, params.force)
                FootballParticles.playKick(player, football, params.force)
                lastActionTick[player.uuid] = now
            }
            FootballActionType.SHOOT -> {
                val params = FootballKickUtil.resolveShootParams(payload.chargeRatio, sprinting)
                FootballKickUtil.applyKickToFootball(
                    player,
                    football,
                    params
                )
                FootballSounds.playKick(player, params.force)
                FootballParticles.playKick(player, football, params.force)
                lastActionTick[player.uuid] = now
            }
            FootballActionType.TRAP -> {
                football.trap()
                FootballSounds.playTrap(player)
                FootballParticles.playTrap(player, football)
                lastActionTick[player.uuid] = now
            }
            FootballActionType.CHIP -> {
                val params = FootballKickUtil.resolveChipParams(player)
                FootballKickUtil.applyKickToFootball(
                    player,
                    football,
                    params
                )
                FootballSounds.playKick(player, params.force)
                FootballParticles.playKick(player, football, params.force)
                lastActionTick[player.uuid] = now
            }
            FootballActionType.DRIBBLE_HOLD, FootballActionType.DRIBBLE_END,
            FootballActionType.GK_CATCH, FootballActionType.GK_DIVE, FootballActionType.GK_PUNCH,
            FootballActionType.GK_THROW_SHORT, FootballActionType.GK_THROW_LONG, FootballActionType.GK_DROP,
            FootballActionType.ITEM_THROW,
            -> Unit
        }
    }

    private fun canAct(player: Player): Boolean = player.mainHandItem.isEmpty
}
