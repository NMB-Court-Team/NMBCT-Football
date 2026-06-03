package net.astrorbits.football.input

import net.astrorbits.football.FootballParticles
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.config.FootballConfigs
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.stamina.StaminaState
import net.astrorbits.football.util.FootballKickUtil
import net.astrorbits.football.util.Vec3Math
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SlideTackleSession(
    val playerId: UUID,
    val direction: Vec3,
    val startTick: Long,
    var endRequested: Boolean = false,
    val playersContacted: MutableSet<UUID> = mutableSetOf(),
    var contactSpeedScale: Double = 1.0,
    var sustainBudgetRemaining: Float = 0f,
)

data class TackledResistance(
    val expiresAtTick: Long,
    val factor: Double,
)

object SlideTackleSessions {
    private const val STEP_SOUND_INTERVAL_TICKS = 4L
    private const val CONTACT_HITBOX_EXPAND = 0.6
    private const val SLIDE_MOVE_EPSILON = 0.001

    private fun slideCfg() = FootballConfigs.server.playerInput.slide

    private val sessions = ConcurrentHashMap<UUID, SlideTackleSession>()
    private val cooldownUntil = ConcurrentHashMap<UUID, Long>()
    private val tackledResistanceUntil = ConcurrentHashMap<UUID, TackledResistance>()
    private val tackledJumpBlockUntil = ConcurrentHashMap<UUID, Long>()
    private val sprintTickState = ConcurrentHashMap<UUID, SprintTickState>()

    private data class SprintTickState(var consecutiveTicks: Int, var lastUpdatedTick: Long)

    fun registerEvents() {
        ServerTickEvents.START_SERVER_TICK.register(::tick)
    }

    fun begin(
        player: ServerPlayer,
        now: Long,
    ): Boolean {
        if (!player.isSprinting || !player.onGround()) {
            return false
        }
        advanceSprintTick(player, now)
        if (getSprintTicks(player) < FootballInputConfig.SLIDE_MIN_SPRINT_TICKS) {
            return false
        }
        val cooldown = cooldownUntil[player.uuid] ?: 0L
        if (now < cooldown) {
            return false
        }

        val direction = FootballKickUtil.resolveDribbleDirection(player)
        if (direction.lengthSqr() < 1.0e-8) {
            return false
        }

        val slide = slideCfg()
        val stamina = FootballConfigs.server.staminaMechanism
        if (StaminaState.getStamina(player.uuid) < stamina.slideTackleEntryCost) {
            return false
        }
        if (!StaminaState.tryConsume(player, stamina.slideTackleEntryCost)) {
            return false
        }

        end(player)
        resetSprintTicks(player.uuid)
        val sustain = stamina.slideTackleSustainCost.coerceAtMost(
            stamina.slideTackleMaxTotalCost - stamina.slideTackleEntryCost,
        )
        sessions[player.uuid] = SlideTackleSession(
            playerId = player.uuid,
            direction = Vec3Math.normalizeSafe(direction),
            startTick = now,
            sustainBudgetRemaining = sustain.coerceAtLeast(0f),
        )
        setSlideState(player, sliding = true)
        FootballNetworking.syncSlideTackleState(player, sliding = true, cooldownUntilTick = 0L)
        FootballParticles.playSlideTackle(player)
        return true
    }

    fun isSliding(player: ServerPlayer): Boolean = sessions.containsKey(player.uuid)

    @JvmStatic
    fun isSliding(playerId: UUID): Boolean = sessions.containsKey(playerId)

    fun getCooldownUntilTick(playerId: UUID): Long = cooldownUntil[playerId] ?: 0L

    /** 本 tick 滑铲推进水平速度（与 [applySlideMovement] 一致，供球碰撞在实体 tick 前使用）。 */
    fun effectiveHorizontalVelocity(player: ServerPlayer): Vec3? {
        val session = sessions[player.uuid] ?: return null
        val now = player.level()?.gameTime ?: return null
        val speed = slideSpeedAtTick(now - session.startTick, session.contactSpeedScale)
        return if (speed > SLIDE_MOVE_EPSILON) session.direction.scale(speed) else null
    }

    @JvmStatic
    fun isTackledJumpBlocked(playerId: UUID, nowTick: Long): Boolean {
        val expiresAtTick = tackledJumpBlockUntil[playerId] ?: return false
        return nowTick <= expiresAtTick
    }

    fun end(player: ServerPlayer) {
        val session = sessions.remove(player.uuid) ?: return
        val now = player.level().gameTime
        applySlideExitVelocity(player, session, now - session.startTick)
        finishSlideSession(player, now)
    }

    fun requestEnd(player: ServerPlayer, now: Long) {
        val session = sessions[player.uuid] ?: return
        val elapsed = now - session.startTick
        if (elapsed >= slideCfg().minSlideTicks) {
            end(player)
            return
        }
        session.endRequested = true
    }

    fun tick(server: MinecraftServer) {
        val now = server.overworld().gameTime
        for (player in server.playerList.players) {
            advanceSprintTick(player, now)
        }
        applyTackledResistance(server, now)
        clearExpiredTackledJumpBlock(now)

        if (sessions.isEmpty()) {
            return
        }

        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            val (_, session) = iterator.next()
            val player = server.playerList.getPlayer(session.playerId)
            if (player == null || !player.isAlive) {
                iterator.remove()
                continue
            }
            if (shouldForceEndSlide(player)) {
                finishSlideSession(player, now)
                iterator.remove()
                continue
            }

            val elapsed = now - session.startTick
            if (session.endRequested && elapsed >= slideCfg().minSlideTicks) {
                applySlideExitVelocity(player, session, elapsed)
                finishSlideSession(player, now)
                iterator.remove()
                continue
            }

            if (!tickSlideStaminaDrain(player, session)) {
                applySlideExitVelocity(player, session, elapsed)
                finishSlideSession(player, now)
                iterator.remove()
                continue
            }

            val speed = applySlideMovement(player, session.direction, elapsed, session.contactSpeedScale)
            if (speed <= SLIDE_MOVE_EPSILON && elapsed >= slideCfg().minSlideTicks) {
                applySlideExitVelocity(player, session, elapsed)
                finishSlideSession(player, now)
                iterator.remove()
                continue
            }
            if (speed > SLIDE_MOVE_EPSILON && elapsed > 0L && elapsed % STEP_SOUND_INTERVAL_TICKS == 0L) {
                FootballSounds.playSlideTackle(player)
            }
            tryResolvePlayerContact(player, session, now)
        }
    }

    private fun tickSlideStaminaDrain(player: ServerPlayer, session: SlideTackleSession): Boolean {
        val budget = session.sustainBudgetRemaining
        if (budget <= 0f) {
            return true
        }
        val decayTicks = slideCfg().decayTicks.coerceAtLeast(1).toLong()
        val perTick = budget / decayTicks.toFloat()
        val cost = perTick.coerceAtMost(budget)
        if (!StaminaState.tryConsume(player, cost)) {
            session.sustainBudgetRemaining = 0f
            return false
        }
        session.sustainBudgetRemaining = (budget - cost).coerceAtLeast(0f)
        return true
    }

    private fun applySlideExitVelocity(player: ServerPlayer, session: SlideTackleSession, elapsed: Long) {
        val speed = slideSpeedAtTick(elapsed, session.contactSpeedScale) * slideCfg().endSpeedRetain
        if (speed > SLIDE_MOVE_EPSILON) {
            val horizontal = session.direction.scale(speed)
            player.setDeltaMovement(horizontal.x, player.deltaMovement.y, horizontal.z)
            player.hurtMarked = true
        }
    }

    private fun finishSlideSession(player: ServerPlayer, now: Long) {
        sessions.remove(player.uuid)
        setSlideState(player, sliding = false)
        val cooldownEnd = now + slideCfg().cooldownTicks
        cooldownUntil[player.uuid] = cooldownEnd
        FootballNetworking.syncSlideTackleState(player, sliding = false, cooldownUntilTick = cooldownEnd)
        resetSprintTicks(player.uuid)
    }

    private fun shouldForceEndSlide(player: ServerPlayer): Boolean {
        return player.isSpectator || player.abilities.flying || player.isFallFlying
    }

    private fun slideSpeedAtTick(elapsed: Long, contactSpeedScale: Double): Double {
        val slide = slideCfg()
        val holdTicks = slide.initialHoldTicks.toLong()
        val decayTicks = slide.decayTicks.coerceAtLeast(1).toLong()
        val speed = if (elapsed < holdTicks) {
            slide.initialSpeed
        } else {
            val decayElapsed = elapsed - holdTicks
            val decayRatio = (decayElapsed.toDouble() / decayTicks.toDouble()).coerceIn(0.0, 1.0)
            slide.initialSpeed * (1.0 - decayRatio)
        }
        return speed * contactSpeedScale.coerceIn(0.0, 1.0)
    }

    private fun applySlideMovement(player: ServerPlayer, direction: Vec3, elapsed: Long, contactSpeedScale: Double): Double {
        val effectiveSpeed = slideSpeedAtTick(elapsed, contactSpeedScale)
        val horizontalVelocity = if (effectiveSpeed > SLIDE_MOVE_EPSILON) direction.scale(effectiveSpeed) else Vec3.ZERO
        player.setDeltaMovement(horizontalVelocity.x, player.deltaMovement.y, horizontalVelocity.z)
        player.hurtMarked = true
        return effectiveSpeed
    }

    private fun setSlideState(player: ServerPlayer, sliding: Boolean) {
        player.isSlideTackling = sliding
        player.refreshDimensions()
    }

    private fun tryResolvePlayerContact(player: ServerPlayer, session: SlideTackleSession, now: Long) {
        val contactBox = expandedContactBox(player)
        val nearbyPlayers = player.level().getEntitiesOfClass(ServerPlayer::class.java, contactBox)
            .filter { other -> other.uuid != session.playerId && other.isAlive && !other.isSpectator }
        if (nearbyPlayers.isEmpty()) return

        val pushSpeed = FootballInputConfig.SLIDE_VICTIM_PUSH_SPEED.coerceAtLeast(0.0)
        val resistanceTicks = FootballInputConfig.SLIDE_VICTIM_RESISTANCE_TICKS.coerceAtLeast(0)
        val resistanceFactor = FootballInputConfig.SLIDE_VICTIM_RESISTANCE_FACTOR.coerceIn(0.0, 1.0)
        val jumpBlockTicks = FootballInputConfig.SLIDE_VICTIM_JUMP_BLOCK_TICKS.coerceAtLeast(0)
        val pushDirection = Vec3Math.normalizeSafe(Vec3Math.horizontal(session.direction))

        var didContact = false
        for (other in nearbyPlayers) {
            if (!session.playersContacted.add(other.uuid)) {
                continue
            }
            val victimPush = if (pushDirection.lengthSqr() > 1.0e-8) {
                pushDirection.scale(pushSpeed)
            } else {
                Vec3.ZERO
            }
            other.setDeltaMovement(other.deltaMovement.add(victimPush.x, 0.0, victimPush.z))
            other.hurtMarked = true
            if (resistanceTicks > 0 && resistanceFactor < 1.0) {
                tackledResistanceUntil[other.uuid] = TackledResistance(
                    expiresAtTick = now + resistanceTicks,
                    factor = resistanceFactor,
                )
            }
            if (jumpBlockTicks > 0) {
                tackledJumpBlockUntil[other.uuid] = now + jumpBlockTicks
            }
            didContact = true
        }

        if (!didContact) return
        val damp = FootballInputConfig.SLIDE_TACKLER_SPEED_DAMP_ON_CONTACT.coerceIn(0.0, 1.0)
        session.contactSpeedScale = (session.contactSpeedScale * damp).coerceIn(0.0, 1.0)
    }

    private fun applyTackledResistance(server: MinecraftServer, now: Long) {
        if (tackledResistanceUntil.isEmpty()) return
        val iterator = tackledResistanceUntil.entries.iterator()
        while (iterator.hasNext()) {
            val (playerId, debuff) = iterator.next()
            if (now > debuff.expiresAtTick) {
                iterator.remove()
                continue
            }
            val player = server.playerList.getPlayer(playerId)
            if (player == null || !player.isAlive || player.isSpectator) {
                iterator.remove()
                continue
            }
            val velocity = player.deltaMovement
            player.setDeltaMovement(velocity.x * debuff.factor, velocity.y, velocity.z * debuff.factor)
            player.hurtMarked = true
        }
    }

    private fun clearExpiredTackledJumpBlock(now: Long) {
        if (tackledJumpBlockUntil.isEmpty()) return
        val iterator = tackledJumpBlockUntil.entries.iterator()
        while (iterator.hasNext()) {
            val (_, expiresAtTick) = iterator.next()
            if (now > expiresAtTick) {
                iterator.remove()
            }
        }
    }

    private fun expandedContactBox(player: ServerPlayer): AABB {
        val box = player.boundingBox
        return box.inflate(CONTACT_HITBOX_EXPAND, 0.0, CONTACT_HITBOX_EXPAND)
    }

    private fun advanceSprintTick(player: ServerPlayer, now: Long) {
        if (isSliding(player)) {
            resetSprintTicks(player.uuid)
            return
        }
        sprintTickState.compute(player.uuid) { _, existing ->
            if (!player.isSprinting) {
                return@compute null
            }
            when {
                existing == null -> SprintTickState(1, now)
                existing.lastUpdatedTick == now -> existing
                existing.lastUpdatedTick == now - 1 -> SprintTickState(existing.consecutiveTicks + 1, now)
                else -> SprintTickState(1, now)
            }
        }
    }

    private fun getSprintTicks(player: ServerPlayer): Int =
        sprintTickState[player.uuid]?.consecutiveTicks ?: 0

    private fun resetSprintTicks(playerId: UUID) {
        sprintTickState.remove(playerId)
    }
}
