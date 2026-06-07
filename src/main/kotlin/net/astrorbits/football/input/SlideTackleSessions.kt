package net.astrorbits.football.input

import net.astrorbits.football.FootballParticles
import net.astrorbits.football.config.FootballConfigs
import net.astrorbits.football.input.SlideTackleSessions.applySlideMovement
import net.astrorbits.football.match.MatchFieldAreaUtil
import net.astrorbits.football.match.MatchParticipation
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.PenaltyKickAwards
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
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class SlideTackleSession(
    val playerId: UUID,
    val direction: Vec3,
    val startTick: Long,
    var endRequested: Boolean = false,
    val playersContacted: MutableSet<UUID> = mutableSetOf(),
    var contactSpeedScale: Double = 1.0,
    /** 撞人后叠加的虚拟 elapsed，使位移曲线提前衰减以缩短剩余滑铲距离。 */
    var contactElapsedPenalty: Long = 0L,
    var sustainBudgetRemaining: Float = 0f,
)

data class TackledResistance(
    val expiresAtTick: Long,
    val factor: Double,
)

object SlideTackleSessions {
    private const val CONTACT_HITBOX_EXPAND = 0.6
    private const val SLIDE_MOVE_EPSILON = SlideTackleSoundTiming.MOVE_EPSILON

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
        if (!MatchParticipation.isParticipating(player)) {
            return false
        }
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
        FootballDribbleSessions.end(player)
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
        val elapsed = effectiveSlideElapsed(session, now - session.startTick)
        val speed = slideSpeedAtTick(elapsed, session.contactSpeedScale)
        return if (speed > SLIDE_MOVE_EPSILON) session.direction.scale(speed) else null
    }

    /** 滑铲水平朝向（单位向量），供铲到球时沿铲向普通踢球。 */
    fun slideKickDirection(player: ServerPlayer): Vec3? {
        val direction = sessions[player.uuid]?.direction ?: return null
        return if (direction.lengthSqr() > 1.0e-8) direction else null
    }

    @JvmStatic
    fun isTackledJumpBlocked(playerId: UUID, nowTick: Long): Boolean {
        val expiresAtTick = tackledJumpBlockUntil[playerId] ?: return false
        return nowTick <= expiresAtTick
    }

    fun end(player: ServerPlayer) {
        val session = sessions[player.uuid] ?: return
        val now = player.level().gameTime
        val elapsed = effectiveSlideElapsed(session, now - session.startTick)
        applySlideExitVelocity(player, session, elapsed)
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

            val rawElapsed = now - session.startTick
            val elapsed = effectiveSlideElapsed(session, rawElapsed)
            if (session.endRequested && rawElapsed >= slideCfg().minSlideTicks) {
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
            if (speed <= SLIDE_MOVE_EPSILON && rawElapsed >= slideCfg().minSlideTicks) {
                applySlideExitVelocity(player, session, elapsed)
                finishSlideSession(player, now)
                iterator.remove()
                continue
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

    private fun effectiveSlideElapsed(session: SlideTackleSession, rawElapsed: Long): Long =
        (rawElapsed + session.contactElapsedPenalty).coerceAtLeast(0L)

    private fun slideSpeedAtTick(elapsed: Long, contactSpeedScale: Double): Double =
        SlideTackleSoundTiming.slideSpeedAtTick(slideCfg(), elapsed, contactSpeedScale)

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

        val slide = slideCfg()
        val pushSpeed = slide.victimPushSpeed.coerceAtLeast(0.0)
        val knockbackUpward = slide.victimKnockbackUpward.coerceAtLeast(0.0)
        val resistanceTicks = FootballInputConfig.SLIDE_VICTIM_RESISTANCE_TICKS.coerceAtLeast(0)
        val resistanceFactor = FootballInputConfig.SLIDE_VICTIM_RESISTANCE_FACTOR.coerceIn(0.0, 1.0)
        val jumpBlockTicks = FootballInputConfig.SLIDE_VICTIM_JUMP_BLOCK_TICKS.coerceAtLeast(0)
        val slideDirection = Vec3Math.normalizeSafe(Vec3Math.horizontal(session.direction))

        var didContact = false
        var foulAwarded = false
        for (other in nearbyPlayers) {
            if (!session.playersContacted.add(other.uuid)) {
                continue
            }
            if (tryAwardSlideTacklePenaltyFoul(player, other)) {
                foulAwarded = true
            }
            val awayFromTackler = Vec3Math.normalizeSafe(
                Vec3Math.horizontal(other.position().subtract(player.position())),
                slideDirection,
            )
            if (awayFromTackler.lengthSqr() > 1.0e-8 && pushSpeed > 0.0) {
                other.setDeltaMovement(
                    awayFromTackler.x * pushSpeed,
                    knockbackUpward,
                    awayFromTackler.z * pushSpeed,
                )
            }
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
        if (foulAwarded) {
            end(player)
            return
        }
        val damp = slide.tacklerSpeedDampOnContact.coerceIn(0.0, 1.0)
        session.contactSpeedScale = (session.contactSpeedScale * damp).coerceIn(0.0, 1.0)
        session.contactElapsedPenalty += slide.contactDistancePenaltyTicks.coerceAtLeast(0).toLong()
    }

    private fun tryAwardSlideTacklePenaltyFoul(tackler: ServerPlayer, victim: ServerPlayer): Boolean {
        if (!MatchParticipation.isParticipating(tackler) || !MatchParticipation.isParticipating(victim)) {
            return false
        }
        val tacklerTeam = MatchState.getPlayerTeam(tackler.uuid) ?: return false
        val victimTeam = MatchState.getPlayerTeam(victim.uuid) ?: return false
        if (tacklerTeam == victimTeam) return false
        if (!MatchFieldAreaUtil.isPlayerInPenaltyArea(tackler, tacklerTeam)) return false
        val level = tackler.level() as? ServerLevel ?: return false
        return PenaltyKickAwards.awardSlideTackleInPenaltyArea(
            level,
            foulingTeam = tacklerTeam,
            foulingPlayerUuid = tackler.uuid,
            fouledPlayerUuid = victim.uuid,
        )
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
