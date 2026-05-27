package net.astrorbits.football.input

import net.astrorbits.football.network.FootballActionPayload
import net.astrorbits.football.network.FootballActionType
import net.astrorbits.football.util.FootballKickUtil
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object FootballPlayerActions {
    private val lastActionTick = ConcurrentHashMap<UUID, Long>()

    fun handle(player: ServerPlayer, payload: FootballActionPayload) {
        if (!canAct(player)) {
            return
        }

        val football = FootballKickUtil.findNearestFootball(player, FootballInputConfig.PLAYER_KICK_RANGE)
            ?: return

        if (player.distanceToSqr(football) > FootballInputConfig.PLAYER_KICK_RANGE * FootballInputConfig.PLAYER_KICK_RANGE) {
            return
        }

        if (!FootballKickUtil.isBallInFront(player, football)) {
            return
        }

        val now = player.level().gameTime
        val last = lastActionTick[player.uuid] ?: Long.MIN_VALUE
        if (now - last < FootballInputConfig.ACTION_COOLDOWN_TICKS) {
            return
        }

        val sprinting = payload.flags and FootballInputConfig.FLAG_SPRINT != 0

        when (payload.action) {
            FootballActionType.PASS -> {
                FootballKickUtil.applyKickToFootball(player, football, FootballKickUtil.resolvePassParams())
                playKickSound(player)
                lastActionTick[player.uuid] = now
            }
            FootballActionType.SHOOT -> {
                FootballKickUtil.applyKickToFootball(
                    player,
                    football,
                    FootballKickUtil.resolveShootParams(payload.chargeRatio, sprinting)
                )
                playKickSound(player)
                lastActionTick[player.uuid] = now
            }
            FootballActionType.DRIBBLE -> {
                if (!hasMovementInput(player)) {
                    return
                }
                FootballKickUtil.applyDribbleToFootball(player, football)
                playDribbleSound(player)
                lastActionTick[player.uuid] = now
            }
            FootballActionType.TRAP -> {
                football.trap()
                playTrapSound(player)
                lastActionTick[player.uuid] = now
            }
            FootballActionType.CHIP -> {
                FootballKickUtil.applyKickToFootball(
                    player,
                    football,
                    FootballKickUtil.resolveChipParams(player)
                )
                playKickSound(player)
                lastActionTick[player.uuid] = now
            }
        }
    }

    private fun canAct(player: Player): Boolean = player.mainHandItem.isEmpty

    private fun hasMovementInput(player: Player): Boolean {
        val moveX = player.xxa.toDouble()
        val moveZ = player.zza.toDouble()
        return moveX * moveX + moveZ * moveZ > 1.0e-4
    }

    private fun playKickSound(player: ServerPlayer) {
        player.level().playSound(
            null,
            player.blockPosition(),
            SoundEvents.PLAYER_ATTACK_STRONG,
            SoundSource.PLAYERS,
            0.55f,
            1.05f + player.random.nextFloat() * 0.1f
        )
    }

    private fun playDribbleSound(player: ServerPlayer) {
        player.level().playSound(
            null,
            player.blockPosition(),
            SoundEvents.SLIME_BLOCK_STEP,
            SoundSource.PLAYERS,
            0.35f,
            1.15f
        )
    }

    private fun playTrapSound(player: ServerPlayer) {
        player.level().playSound(
            null,
            player.blockPosition(),
            SoundEvents.WOOL_STEP,
            SoundSource.PLAYERS,
            0.45f,
            0.85f
        )
    }
}
