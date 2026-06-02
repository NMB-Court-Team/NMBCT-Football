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
    private val MODIFIER_ID = Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "stamina_speed")

    var stamina: Float = 0f
        private set

    var maxStamina: Float = 1000f
        private set

    fun applySync(stamina: Float, maxStamina: Float) {
        this.stamina = stamina.coerceAtLeast(0f)
        this.maxStamina = maxStamina.coerceAtLeast(1f)
    }

    fun getSpeedMultiplier(): Double {
        val cfg = FootballConfigs.server.staminaMechanism
        return cfg.speedMultiplierForStamina(stamina).toDouble()
    }

    /** 移速档位阈值（0..1），用于 HUD 刻度；不含隐式 100%。 */
    fun hudTierFractions(): List<Float> =
        FootballConfigs.server.staminaMechanism.sortedSpeedTiers()
            .map { it.staminaFraction }
            .filter { it > 0f && it < 1f }
            .distinct()
            .sortedDescending()

    fun tick(client: Minecraft) {
        val player = client.player ?: return

        if (player.isSpectator || player.isCreative) {
            val max = FootballConfigs.server.staminaMechanism.maxStamina
            applySync(max, max)
            clearSpeedModifier(player)
            return
        }

        applySpeedModifier(player)
    }

    private fun applySpeedModifier(player: net.minecraft.client.player.LocalPlayer) {
        val attr = player.getAttribute(Attributes.MOVEMENT_SPEED) ?: return
        val mult = getSpeedMultiplier()

        if (mult >= 1.0) {
            attr.removeModifier(MODIFIER_ID)
            return
        }

        val needed = mult - 1.0
        val existing = attr.getModifier(MODIFIER_ID)
        if (existing == null || existing.amount != needed) {
            attr.removeModifier(MODIFIER_ID)
            attr.addTransientModifier(
                AttributeModifier(MODIFIER_ID, needed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
            )
        }
    }

    private fun clearSpeedModifier(player: net.minecraft.client.player.LocalPlayer) {
        player.getAttribute(Attributes.MOVEMENT_SPEED)?.removeModifier(MODIFIER_ID)
    }
}
