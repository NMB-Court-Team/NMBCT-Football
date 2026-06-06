package net.astrorbits.football.stamina

import net.astrorbits.football.FootballParticles
import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.config.FootballConfigs
import net.astrorbits.football.input.FootballMovementInputUtil
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object BoostSprintState {
    private val MODIFIER_ID = Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "boost_sprint_speed")
    private val activeByPlayer = ConcurrentHashMap<UUID, Boolean>()
    private val trailTickByPlayer = ConcurrentHashMap<UUID, Int>()

    fun isActive(playerId: UUID): Boolean = activeByPlayer[playerId] == true

    fun setRequested(player: ServerPlayer, enabled: Boolean) {
        if (player.isSpectator) {
            setActive(player, false)
            return
        }
        if (!enabled) {
            setActive(player, false)
            return
        }
        if (!player.isSprinting || !FootballMovementInputUtil.hasSprintForwardImpulse(player) || staminaBlocksBoost(player)) {
            setActive(player, false)
            return
        }
        setActive(player, true)
    }

    fun tickPlayer(player: ServerPlayer) {
        if (player.isSpectator) {
            setActive(player, false)
            return
        }
        if (!isActive(player.uuid)) {
            return
        }
        if (!player.isSprinting || !FootballMovementInputUtil.hasSprintForwardImpulse(player) || staminaBlocksBoost(player)) {
            setActive(player, false)
            return
        }
        applySpeedModifier(player)
        val ticks = trailTickByPlayer.compute(player.uuid) { _, old -> (old ?: 0) + 1 } ?: 1
        if (ticks % 3 == 0) {
            FootballParticles.playBoostSprintTrail(player)
        }
    }

    fun removePlayer(playerId: UUID) {
        activeByPlayer.remove(playerId)
        trailTickByPlayer.remove(playerId)
    }

    private fun setActive(player: ServerPlayer, active: Boolean) {
        val id = player.uuid
        val was = activeByPlayer[id] == true
        if (active) {
            activeByPlayer[id] = true
            applySpeedModifier(player)
        } else {
            activeByPlayer.remove(id)
            clearSpeedModifier(player)
        }
        if (was != active) {
            StaminaState.syncToPlayer(player)
        }
    }

    private fun applySpeedModifier(player: ServerPlayer) {
        val cfg = FootballConfigs.server.staminaMechanism
        val mult = cfg.boostSprintSpeedMultiplier.toDouble()
        val attr = player.getAttribute(Attributes.MOVEMENT_SPEED) ?: return
        val needed = mult - 1.0
        val existing = attr.getModifier(MODIFIER_ID)
        if (existing == null || existing.amount != needed) {
            attr.removeModifier(MODIFIER_ID)
            attr.addTransientModifier(
                AttributeModifier(MODIFIER_ID, needed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
            )
        }
    }

    private fun clearSpeedModifier(player: ServerPlayer) {
        player.getAttribute(Attributes.MOVEMENT_SPEED)?.removeModifier(MODIFIER_ID)
    }

    private fun staminaBlocksBoost(player: ServerPlayer): Boolean =
        !player.isCreative && StaminaState.getStamina(player.uuid) <= 0f
}
