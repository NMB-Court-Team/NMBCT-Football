package net.astrorbits.football.input

import net.astrorbits.football.FootballParticles
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.network.FootballNetworking
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
)

data class TackledResistance(
    val expiresAtTick: Long,
    val factor: Double,
)

object SlideTackleSessions {
    // how many ticks at least will stay in slide pose after pressed key
    private const val MIN_SLIDE_TICKS = 30L
    private const val SLIDE_COOLDOWN_TICKS = 14L
    private const val STEP_SOUND_INTERVAL_TICKS = 4L
    private const val CONTACT_HITBOX_EXPAND = 0.6
    // 进入滑铲时略快于疾跑，先保持一小段时间，再衰减到 0。
    private const val SLIDE_INITIAL_SPEED = 0.8
    // 保持匀速时间。
    private const val SLIDE_INITIAL_HOLD_TICKS = 4L
    // 速度衰减时间。
    private const val SLIDE_DECAY_TICKS = 10L
    private const val SLIDE_MOVE_EPSILON = 0.001

    private val sessions = ConcurrentHashMap<UUID, SlideTackleSession>()
    private val cooldownUntil = ConcurrentHashMap<UUID, Long>()
    private val tackledResistanceUntil = ConcurrentHashMap<UUID, TackledResistance>()
    private val tackledJumpBlockUntil = ConcurrentHashMap<UUID, Long>()
    private val sprintTickState = ConcurrentHashMap<UUID, SprintTickState>()

    private data class SprintTickState(var consecutiveTicks: Int, var lastUpdatedTick: Long)

    fun registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
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

        end(player)
        resetSprintTicks(player.uuid)
        sessions[player.uuid] = SlideTackleSession(
            playerId = player.uuid,
            direction = Vec3Math.normalizeSafe(direction),
            startTick = now,
        )
        cooldownUntil[player.uuid] = now + SLIDE_COOLDOWN_TICKS
        setSlideState(player, sliding = true)
        FootballNetworking.syncSlideTackleState(player, sliding = true)
        FootballParticles.playSlideTackle(player)
        return true
    }

    fun isSliding(player: ServerPlayer): Boolean = sessions.containsKey(player.uuid)

    @JvmStatic
    fun isSliding(playerId: UUID): Boolean = sessions.containsKey(playerId)

    /** 本 tick 滑铲推进水平速度（与 [applySlideMovement] 一致，供球碰撞在实体 tick 前使用）。 */
    fun effectiveHorizontalVelocity(player: ServerPlayer): Vec3? {
        val session = sessions[player.uuid] ?: return null
        val now = (player.level() as? ServerLevel)?.gameTime ?: return null
        val speed = slideSpeedAtTick(now - session.startTick, session.contactSpeedScale)
        return if (speed > SLIDE_MOVE_EPSILON) session.direction.scale(speed) else null
    }

    @JvmStatic
    fun isTackledJumpBlocked(playerId: UUID, nowTick: Long): Boolean {
        val expiresAtTick = tackledJumpBlockUntil[playerId] ?: return false
        return nowTick <= expiresAtTick
    }

    fun end(player: ServerPlayer) {
        if (sessions.remove(player.uuid) != null) {
            player.setDeltaMovement(0.0, player.deltaMovement.y, 0.0)
            setSlideState(player, sliding = false)
            FootballNetworking.syncSlideTackleState(player, sliding = false)
            resetSprintTicks(player.uuid)
        }
    }

    fun requestEnd(player: ServerPlayer, now: Long) {
        val session = sessions[player.uuid] ?: return
        val elapsed = now - session.startTick
        if (elapsed >= MIN_SLIDE_TICKS) {
            end(player)
            return
        }
        session.endRequested = true
        resetSprintTicks(player.uuid)
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
                finishSlideSession(player)
                iterator.remove()
                continue
            }

            val elapsed = now - session.startTick
            if (session.endRequested && elapsed >= MIN_SLIDE_TICKS) {
                finishSlideSession(player)
                iterator.remove()
                continue
            }

            val speed = applySlideMovement(player, session.direction, elapsed, session.contactSpeedScale)
            if (speed > SLIDE_MOVE_EPSILON && elapsed > 0L && elapsed % STEP_SOUND_INTERVAL_TICKS == 0L) {
                FootballSounds.playSlideTackle(player)
            }
            tryResolvePlayerContact(player, session, now)
        }
    }

    private fun finishSlideSession(player: ServerPlayer) {
        setSlideState(player, sliding = false)
        FootballNetworking.syncSlideTackleState(player, sliding = false)
        resetSprintTicks(player.uuid)
    }

    private fun shouldForceEndSlide(player: ServerPlayer): Boolean {
        return player.isSpectator || player.abilities.flying || player.isFallFlying
    }

    private fun slideSpeedAtTick(elapsed: Long, contactSpeedScale: Double): Double {
        val speed = if (elapsed < SLIDE_INITIAL_HOLD_TICKS) {
            SLIDE_INITIAL_SPEED
        } else {
            val decayElapsed = elapsed - SLIDE_INITIAL_HOLD_TICKS
            val decayRatio = (decayElapsed.toDouble() / SLIDE_DECAY_TICKS.toDouble()).coerceIn(0.0, 1.0)
            SLIDE_INITIAL_SPEED * (1.0 - decayRatio)
        }
        return speed * contactSpeedScale.coerceIn(0.0, 1.0)
    }

    private fun applySlideMovement(player: ServerPlayer, direction: Vec3, elapsed: Long, contactSpeedScale: Double): Double {
        val effectiveSpeed = slideSpeedAtTick(elapsed, contactSpeedScale)
        val horizontalVelocity = if (effectiveSpeed > SLIDE_MOVE_EPSILON) direction.scale(effectiveSpeed) else Vec3.ZERO
        // 滑铲期间持续写入水平速度，让下一 tick 的原版移动链路按该速度推进。
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
        // 接触后持续降低滑铲推进速度，避免下一 tick 被滑铲速度写回。
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
