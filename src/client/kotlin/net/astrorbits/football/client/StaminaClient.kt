package net.astrorbits.football.client

import net.astrorbits.football.NMBCTFootball
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes

object StaminaClient {
    const val MAX_STAMINA = 1000
    private const val JUMP_COST = 60
    private const val SPRINT_STEP_TICKS = 2
    private const val RECOVERY_DELAY_TICKS = 20
    private const val RECOVERY_STEP_TICKS = 2

    private val MODIFIER_ID = Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "stamina_speed")

    var stamina: Int = MAX_STAMINA
        private set

    private var ticksSinceConsume: Int = 0
    private var sprintAccumulator: Int = 0
    private var recoveryAccumulator: Int = 0
    private var wasOnGround: Boolean = false

    fun getSpeedMultiplier(): Double = when {
        stamina <= 0   -> 0.6
        stamina < 100  -> 0.7
        stamina < 400  -> 0.8
        stamina < 800  -> 0.9
        else           -> 1.0
    }

    fun tick(client: Minecraft) {
        val player = client.player ?: return

        if (player.isSpectator || player.isCreative) {
            reset()
            clearSpeedModifier(player)
            return
        }

        var consumed = false

        // 疾跑消耗
        if (player.isSprinting && player.input.hasForwardImpulse()) {
            sprintAccumulator++
            while (sprintAccumulator >= SPRINT_STEP_TICKS) {
                sprintAccumulator -= SPRINT_STEP_TICKS
                stamina = (stamina - 1).coerceAtLeast(0)
                consumed = true
            }
        } else {
            sprintAccumulator = 0
        }

        // 跳跃消耗：在 END_CLIENT_TICK 时玩家已离地，用上一 tick 的 onGround 检测起跳
        val onGround = player.onGround()
        val jumpDown = player.input.keyPresses.jump()
        if (jumpDown && wasOnGround && !onGround) {
            stamina = (stamina - JUMP_COST).coerceAtLeast(0)
            consumed = true
        }
        wasOnGround = onGround

        // 回复
        if (consumed) {
            ticksSinceConsume = 0
            recoveryAccumulator = 0
        } else {
            ticksSinceConsume++
            if (ticksSinceConsume > RECOVERY_DELAY_TICKS) {
                recoveryAccumulator++
                while (recoveryAccumulator >= RECOVERY_STEP_TICKS) {
                    recoveryAccumulator -= RECOVERY_STEP_TICKS
                    stamina = (stamina + 1).coerceAtMost(MAX_STAMINA)
                }
            }
        }

        // 每 tick 确保速度 modifier 存在且数值正确（不用 lastMultiplier 缓存，因为服务端同步可能清掉）
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
                AttributeModifier(MODIFIER_ID, needed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            )
        }
    }

    private fun clearSpeedModifier(player: net.minecraft.client.player.LocalPlayer) {
        player.getAttribute(Attributes.MOVEMENT_SPEED)?.removeModifier(MODIFIER_ID)
    }

    /** 比赛开始时回满体力 */
    fun onMatchStart() {
        stamina = MAX_STAMINA
        ticksSinceConsume = 0
        sprintAccumulator = 0
        recoveryAccumulator = 0
        wasOnGround = false
    }

    /** 半场切换时回复体力 */
    fun onHalfSwitch() {
        stamina = (stamina + 600).coerceAtMost(MAX_STAMINA)
        ticksSinceConsume = 0
        sprintAccumulator = 0
        recoveryAccumulator = 0
    }

    /** 进球后回复体力 */
    fun onGoalScored() {
        stamina = (stamina + 150).coerceAtMost(MAX_STAMINA)
        ticksSinceConsume = 0
        sprintAccumulator = 0
        recoveryAccumulator = 0
    }

    fun reset() {
        stamina = MAX_STAMINA
        ticksSinceConsume = 0
        sprintAccumulator = 0
        recoveryAccumulator = 0
        wasOnGround = false
    }
}
