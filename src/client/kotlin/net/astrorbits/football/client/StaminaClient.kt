package net.astrorbits.football.client

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.config.FootballConfigs
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes

/**
 * 客户端体力展示与移速修正：数值由服务端 [net.astrorbits.football.stamina.StaminaState] 同步。
 */
object StaminaClient {
    private val STAMINA_SPEED_MODIFIER_ID = Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "stamina_speed")
    private val BOOST_SPEED_MODIFIER_ID = Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "boost_sprint_speed")

    var stamina: Float = 0f
        private set

    var maxStamina: Float = 1000f
        private set

    var boostSprintActive: Boolean = false
        private set

    /** 0..1，用于体力条紫色渐变。 */
    var boostBlend: Float = 0f
        private set

    private const val BOOST_BLEND_STEP = 0.125f

    fun applySync(stamina: Float, maxStamina: Float, boostSprintActive: Boolean) {
        applySyncInternal(stamina, maxStamina, boostSprintActive, playBoostSounds = shouldPlayLocalBoostSounds())
    }

    /** 旁观/回放时只更新数值，不触发加速疾跑起止音效。 */
    private fun applySyncInternal(
        stamina: Float,
        maxStamina: Float,
        boostSprintActive: Boolean,
        playBoostSounds: Boolean,
    ) {
        val wasBoost = this.boostSprintActive
        this.stamina = stamina.coerceAtLeast(0f)
        this.maxStamina = maxStamina.coerceAtLeast(1f)
        if (playBoostSounds) {
            if (!boostSprintActive && wasBoost) {
                BoostSprintClient.onServerDeactivated()
                BoostSprintSoundsClient.playEnd()
            }
            if (boostSprintActive && !wasBoost) {
                BoostSprintSoundsClient.playStart()
            }
        } else if (!boostSprintActive && wasBoost) {
            BoostSprintClient.onServerDeactivated()
        }
        this.boostSprintActive = boostSprintActive
    }

    private fun shouldPlayLocalBoostSounds(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return !player.isSpectator
    }

    fun getSpeedMultiplier(): Double {
        val cfg = FootballConfigs.server.staminaMechanism
        var mult = cfg.speedMultiplierForStamina(stamina).toDouble()
        if (boostSprintActive) {
            mult *= cfg.boostSprintSpeedMultiplier
        }
        return mult
    }

    fun hudTierFractions(): List<Float> =
        FootballConfigs.server.staminaMechanism.sortedSpeedTiers()
            .map { it.staminaFraction }
            .filter { it > 0f && it < 1f }
            .distinct()
            .sortedDescending()

    fun tick(client: Minecraft) {
        val player = client.player ?: return

        val targetBlend = if (boostSprintActive) 1f else 0f
        boostBlend = when {
            boostBlend < targetBlend -> (boostBlend + BOOST_BLEND_STEP).coerceAtMost(1f)
            boostBlend > targetBlend -> (boostBlend - BOOST_BLEND_STEP).coerceAtLeast(0f)
            else -> targetBlend
        }

        if (player.isSpectator) {
            val max = FootballConfigs.server.staminaMechanism.maxStamina
            applySyncInternal(max, max, false, playBoostSounds = false)
            clearSpeedModifiers(player)
            return
        }
        if (player.isCreative) {
            val max = FootballConfigs.server.staminaMechanism.maxStamina
            stamina = max
            maxStamina = max
            applyCreativeSpeedModifiers(player)
            return
        }

        applySpeedModifiers(player)
    }

    fun baseStaminaBarColor(ratio: Float): Int = when {
        ratio <= 0f -> 0xFFE53935.toInt()
        ratio < 0.1f -> 0xFFE53935.toInt()
        ratio < 0.4f -> 0xFFFF9800.toInt()
        ratio < 0.8f -> 0xFFFFD54F.toInt()
        else -> 0xFF4CAF50.toInt()
    }

    fun displayStaminaBarColor(ratio: Float): Int {
        val base = baseStaminaBarColor(ratio)
        if (boostBlend <= 0f) {
            return base
        }
        val purple = 0xFF9C27B0.toInt()
        return lerpColor(base, purple, boostBlend)
    }

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val a = ((from shr 24) and 0xFF) + (((to shr 24) and 0xFF) - ((from shr 24) and 0xFF)) * t
        val r = ((from shr 16) and 0xFF) + (((to shr 16) and 0xFF) - ((from shr 16) and 0xFF)) * t
        val g = ((from shr 8) and 0xFF) + (((to shr 8) and 0xFF) - ((from shr 8) and 0xFF)) * t
        val b = (from and 0xFF) + ((to and 0xFF) - (from and 0xFF)) * t
        return ((a.toInt().coerceIn(0, 255) shl 24) or
            (r.toInt().coerceIn(0, 255) shl 16) or
            (g.toInt().coerceIn(0, 255) shl 8) or
            b.toInt().coerceIn(0, 255))
    }

    private fun applySpeedModifiers(player: net.minecraft.client.player.LocalPlayer) {
        val attr = player.getAttribute(Attributes.MOVEMENT_SPEED) ?: return
        val cfg = FootballConfigs.server.staminaMechanism
        val staminaMult = cfg.speedMultiplierForStamina(stamina).toDouble()
        val staminaNeeded = staminaMult - 1.0
        val existingStamina = attr.getModifier(STAMINA_SPEED_MODIFIER_ID)
        if (staminaMult >= 1.0) {
            attr.removeModifier(STAMINA_SPEED_MODIFIER_ID)
        } else if (existingStamina == null || existingStamina.amount != staminaNeeded) {
            attr.removeModifier(STAMINA_SPEED_MODIFIER_ID)
            attr.addTransientModifier(
                AttributeModifier(STAMINA_SPEED_MODIFIER_ID, staminaNeeded, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
            )
        }

        if (boostSprintActive) {
            val boostNeeded = cfg.boostSprintSpeedMultiplier.toDouble() - 1.0
            val existingBoost = attr.getModifier(BOOST_SPEED_MODIFIER_ID)
            if (existingBoost == null || existingBoost.amount != boostNeeded) {
                attr.removeModifier(BOOST_SPEED_MODIFIER_ID)
                attr.addTransientModifier(
                    AttributeModifier(BOOST_SPEED_MODIFIER_ID, boostNeeded, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                )
            }
        } else {
            attr.removeModifier(BOOST_SPEED_MODIFIER_ID)
        }
    }

    private fun applyCreativeSpeedModifiers(player: net.minecraft.client.player.LocalPlayer) {
        val attr = player.getAttribute(Attributes.MOVEMENT_SPEED) ?: return
        attr.removeModifier(STAMINA_SPEED_MODIFIER_ID)
        if (boostSprintActive) {
            val boostNeeded = FootballConfigs.server.staminaMechanism.boostSprintSpeedMultiplier.toDouble() - 1.0
            val existingBoost = attr.getModifier(BOOST_SPEED_MODIFIER_ID)
            if (existingBoost == null || existingBoost.amount != boostNeeded) {
                attr.removeModifier(BOOST_SPEED_MODIFIER_ID)
                attr.addTransientModifier(
                    AttributeModifier(BOOST_SPEED_MODIFIER_ID, boostNeeded, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                )
            }
        } else {
            attr.removeModifier(BOOST_SPEED_MODIFIER_ID)
        }
    }

    private fun clearSpeedModifiers(player: net.minecraft.client.player.LocalPlayer) {
        val attr = player.getAttribute(Attributes.MOVEMENT_SPEED) ?: return
        attr.removeModifier(STAMINA_SPEED_MODIFIER_ID)
        attr.removeModifier(BOOST_SPEED_MODIFIER_ID)
    }
}
