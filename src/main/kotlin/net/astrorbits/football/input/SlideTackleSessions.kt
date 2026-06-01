package net.astrorbits.football.input

import net.astrorbits.football.FootballParticles
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.mixinhelper.SlideTackleStateAccess
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.util.FootballKickUtil
import net.astrorbits.football.util.Vec3Math
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SlideTackleSession(
    val playerId: UUID,
    val direction: Vec3,
    val startTick: Long,
    val lookYaw: Float,
    val lookPitch: Float,
    val sprinting: Boolean,
    var touchResolved: Boolean = false,
    var endRequested: Boolean = false,
    val playersContacted: MutableSet<UUID> = mutableSetOf(),
)

data class TackledResistance(
    val expiresAtTick: Long,
    val factor: Double,
)

object SlideTackleSessions {
    // how many ticks at least will stay in slide pose after pressed key
    private const val MIN_SLIDE_TICKS = 15L
    private const val SLIDE_COOLDOWN_TICKS = 14L
    private const val TOUCH_RANGE_BONUS = 0.75
    private const val CHIP_HEIGHT_THRESHOLD = 0.55
    private const val CHIP_FACING_THRESHOLD = 0.3
    private const val CONTACT_DELAY_TICKS = 3L
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

    fun registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    fun begin(
        player: ServerPlayer,
        now: Long,
        lookYaw: Float,
        lookPitch: Float,
        sprinting: Boolean,
    ): Boolean {
        if (!player.isSprinting || !player.onGround()) {
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
        sessions[player.uuid] = SlideTackleSession(
            playerId = player.uuid,
            direction = Vec3Math.normalizeSafe(direction),
            startTick = now,
            lookYaw = lookYaw,
            lookPitch = lookPitch,
            sprinting = sprinting,
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

    fun end(player: ServerPlayer) {
        if (sessions.remove(player.uuid) != null) {
            player.setDeltaMovement(0.0, player.deltaMovement.y, 0.0)
            setSlideState(player, sliding = false)
            FootballNetworking.syncSlideTackleState(player, sliding = false)
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
    }

    fun tick(server: MinecraftServer) {
        val now = server.overworld().gameTime
        applyTackledResistance(server, now)

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
                iterator.remove()
                setSlideState(player, sliding = false)
                FootballNetworking.syncSlideTackleState(player, sliding = false)
                continue
            }

            val elapsed = now - session.startTick
            if (session.endRequested && elapsed >= MIN_SLIDE_TICKS) {
                iterator.remove()
                setSlideState(player, sliding = false)
                FootballNetworking.syncSlideTackleState(player, sliding = false)
                continue
            }

            val speed = applySlideMovement(player, session.direction, elapsed)
            if (speed > SLIDE_MOVE_EPSILON && elapsed > 0L && elapsed % STEP_SOUND_INTERVAL_TICKS == 0L) {
                FootballSounds.playSlideTackle(player)
            }
            tryResolveBallContact(player, session, elapsed)
            tryResolvePlayerContact(player, session, now)
        }
    }

    private fun shouldForceEndSlide(player: ServerPlayer): Boolean {
        return player.isSpectator || player.abilities.flying || player.isFallFlying
    }

    private fun applySlideMovement(player: ServerPlayer, direction: Vec3, elapsed: Long): Double {
        val speed = if (elapsed < SLIDE_INITIAL_HOLD_TICKS) {
            SLIDE_INITIAL_SPEED
        } else {
            val decayElapsed = elapsed - SLIDE_INITIAL_HOLD_TICKS
            val decayRatio = (decayElapsed.toDouble() / SLIDE_DECAY_TICKS.toDouble()).coerceIn(0.0, 1.0)
            SLIDE_INITIAL_SPEED * (1.0 - decayRatio)
        }
        val horizontalVelocity = if (speed > SLIDE_MOVE_EPSILON) direction.scale(speed) else Vec3.ZERO
        // 滑铲期间持续写入水平速度，让下一 tick 的原版移动链路按该速度推进。
        player.setDeltaMovement(horizontalVelocity.x, player.deltaMovement.y, horizontalVelocity.z)
        player.hurtMarked = true
        return speed
    }

    private fun tryResolveBallContact(player: ServerPlayer, session: SlideTackleSession, elapsed: Long) {
        if (session.touchResolved) {
            return
        }
        if (elapsed < CONTACT_DELAY_TICKS) {
            return
        }
        val range = FootballInputConfig.PLAYER_KICK_RANGE + TOUCH_RANGE_BONUS
        val football = FootballKickUtil.findNearestFootball(player, range) ?: return
        if (football.isHeld()) {
            return
        }
        if (player.distanceToSqr(football) > range * range) {
            return
        }
        if (!expandedContactBox(player).intersects(football.boundingBox)) {
            return
        }

        val ballCenter = football.position().add(0.0, FootballPhysicsConfig.RADIUS, 0.0)
        val toBall = Vec3(ballCenter.x - player.x, 0.0, ballCenter.z - player.z)
        val facing = if (toBall.lengthSqr() > 1.0e-8) {
            Vec3Math.normalizeSafe(toBall).dot(Vec3Math.normalizeSafe(session.direction))
        } else {
            1.0
        }
        val shouldChip = ballCenter.y - player.y > CHIP_HEIGHT_THRESHOLD || facing < CHIP_FACING_THRESHOLD
        val params = if (shouldChip) {
            FootballKickUtil.resolveChipParams(player)
        } else {
            FootballKickUtil.resolveSlideKickParams(session.sprinting)
        }
        FootballKickUtil.applyKickToFootballWithLook(football, params, session.lookYaw, session.lookPitch)
        FootballSounds.playKick(player, params.force)
        FootballParticles.playSlideTackleContact(player, football, params.force)
        session.touchResolved = true
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
            didContact = true
        }

        if (!didContact) return
        val damp = FootballInputConfig.SLIDE_TACKLER_SPEED_DAMP_ON_CONTACT.coerceIn(0.0, 1.0)
        val current = player.deltaMovement
        player.setDeltaMovement(current.x * damp, current.y, current.z * damp)
        player.hurtMarked = true
        // 保持会话方向与速度曲线不变，仅在接触帧快速降速，后续 tick 仍由滑铲逻辑接管。
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

    private fun expandedContactBox(player: ServerPlayer): AABB {
        val box = player.boundingBox
        return box.inflate(CONTACT_HITBOX_EXPAND, 0.0, CONTACT_HITBOX_EXPAND)
    }
}
