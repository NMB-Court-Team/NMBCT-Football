package net.astrorbits.football.input

import net.astrorbits.football.match.PlayerRoleState
import net.astrorbits.football.network.FootballActionC2SPayload
import net.astrorbits.football.network.FootballActionType
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.FootballParticles
import net.astrorbits.football.item.FootballItem
import net.astrorbits.football.Football
import net.astrorbits.football.util.FootballKickUtil
import net.astrorbits.football.util.KickChargeUtil
import net.astrorbits.football.util.Vec3Math
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object FootballPlayerActions {
    private val lastActionTick = ConcurrentHashMap<UUID, Long>()
    private val lastKickAwayActionTick = ConcurrentHashMap<UUID, Long>()

    /** 踢球后在此 tick 数内，若球仍明显远离玩家则拒绝恢复运球。 */
    private const val DRIBBLE_RESUME_VELOCITY_CHECK_TICKS = 20L
    private const val DRIBBLE_RESUME_MIN_BALL_SPEED_SQR = 0.01

    fun handle(player: ServerPlayer, payload: FootballActionC2SPayload) {
        // TODO: 服务端双重保险 — 开球锁定时拒绝接收此玩家的足球操作包
        net.astrorbits.football.match.MatchState.notifyKickoffBallTouched(player)
        if (payload.action == FootballActionType.ITEM_THROW) {
            FootballItem.tryThrowFromMainHand(player)
            return
        }
        if (PlayerRoleState.isGoalkeeper(player)) {
            GoalkeeperActions.handle(player, payload)
            return
        }
        if (isBlockedBySlideState(player, payload.action)) {
            FootballDribbleSessions.end(player)
            return
        }

        when (payload.action) {
            FootballActionType.DRIBBLE_HOLD -> handleDribbleHold(player, payload)
            FootballActionType.DRIBBLE_END -> FootballDribbleSessions.end(player)
            FootballActionType.SLIDE_TACKLE -> handleSlideTackle(player, payload)
            FootballActionType.SLIDE_TACKLE_END -> handleSlideTackleEnd(player)
            else -> handleKickAction(player, payload)
        }
    }

    private fun isBlockedBySlideState(player: ServerPlayer, action: FootballActionType): Boolean {
        if (!SlideTackleSessions.isSliding(player)) {
            return false
        }
        return action != FootballActionType.SLIDE_TACKLE_END
    }

    private fun handleSlideTackle(player: ServerPlayer, payload: FootballActionC2SPayload) {
        FootballDribbleSessions.end(player)
        if (
            !canAct(player) ||
            !player.isSprinting ||
            !player.onGround() ||
            GoalkeeperDiveSessions.isDiving(player) ||
            SlideTackleSessions.isSliding(player)
        ) {
            return
        }
        val now = player.level().gameTime
        val sprinting = payload.flags and FootballInputConfig.FLAG_SPRINT != 0
        SlideTackleSessions.begin(
            player = player,
            now = now,
            lookYaw = payload.lookYaw,
            lookPitch = payload.lookPitch,
            sprinting = sprinting,
        )
    }

    private fun handleSlideTackleEnd(player: ServerPlayer) {
        SlideTackleSessions.requestEnd(player, player.level().gameTime)
    }

    private fun handleDribbleHold(player: ServerPlayer, payload: FootballActionC2SPayload) {
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

        if (!hasDribbleMovementInput(player, payload)) {
            FootballDribbleSessions.end(player)
            return
        }

        val now = player.level().gameTime
        if (shouldBlockDribbleAfterKickAway(player, football, now)) {
            FootballDribbleSessions.end(player)
            return
        }

        football.lastKicker = player.uuid
        FootballDribbleSessions.beginOrRefresh(player, football, now, payload)
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
                FootballKickUtil.applyKickToFootball(player, football, params, applySpread = true)
                FootballSounds.playKick(player, params.force)
                FootballParticles.playKick(player, football, params.force)
                markKickAwayAction(player, now)
            }
            FootballActionType.SHOOT -> {
                val chargeSettings = FootballInputConfig.chargeSettings()
                val perfect = KickChargeUtil.isPerfectCharge(payload.chargeHeldMs, chargeSettings)
                val chargeRatio = KickChargeUtil.computeRatio(payload.chargeHeldMs, chargeSettings)
                val params = FootballKickUtil.resolveShootParams(chargeRatio, sprinting, perfect)
                FootballKickUtil.applyKickToFootball(player, football, params, applySpread = !perfect)
                FootballSounds.playKick(player, params.force)
                FootballParticles.playKick(player, football, params.force)
                markKickAwayAction(player, now)
            }
            FootballActionType.TRAP -> {
                football.lastKicker = player.uuid
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
                markKickAwayAction(player, now)
            }
            FootballActionType.DRIBBLE_HOLD, FootballActionType.DRIBBLE_END,
            FootballActionType.GK_CATCH, FootballActionType.GK_DIVE, FootballActionType.GK_PUNCH,
            FootballActionType.GK_THROW_SHORT, FootballActionType.GK_THROW_LONG, FootballActionType.GK_DROP,
            FootballActionType.ITEM_THROW, FootballActionType.SLIDE_TACKLE, FootballActionType.SLIDE_TACKLE_END,
            -> Unit
        }
    }

    private fun canAct(player: Player): Boolean = player.mainHandItem.isEmpty

    private fun hasDribbleMovementInput(player: ServerPlayer, payload: FootballActionC2SPayload): Boolean {
        if (payload.flags and FootballInputConfig.FLAG_LOOK_AROUND != 0) {
            return FootballMovementInputUtil.hasMovementInput(player, payload.lookYaw)
        }
        return FootballMovementInputUtil.hasMovementInput(player)
    }

    private fun markKickAwayAction(player: ServerPlayer, now: Long) {
        lastActionTick[player.uuid] = now
        lastKickAwayActionTick[player.uuid] = now
    }

    private fun shouldBlockDribbleAfterKickAway(player: ServerPlayer, football: Football, now: Long): Boolean {
        val lastKick = lastKickAwayActionTick[player.uuid] ?: return false
        if (now - lastKick > DRIBBLE_RESUME_VELOCITY_CHECK_TICKS) {
            return false
        }

        val toBall = Vec3Math.horizontal(football.position().subtract(player.position()))
        if (toBall.lengthSqr() < 1.0e-8) {
            return false
        }

        val speed = Vec3Math.horizontal(football.getPhysicsState().linearVelocity)
        if (speed.lengthSqr() < DRIBBLE_RESUME_MIN_BALL_SPEED_SQR) {
            return false
        }

        return toBall.normalize().dot(speed.normalize()) > 0.0
    }
}
