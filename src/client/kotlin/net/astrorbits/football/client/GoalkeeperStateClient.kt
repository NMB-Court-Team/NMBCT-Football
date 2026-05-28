package net.astrorbits.football.client

import net.astrorbits.football.Football
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.astrorbits.football.network.GoalkeeperHoldLockS2CPayload
import net.astrorbits.football.network.GoalkeeperRoleS2CPayload
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer

object GoalkeeperStateClient {
    var isGoalkeeper: Boolean = false
        private set

    var isHoldingBall: Boolean = false
        private set

    /** 释放锁定结束时的世界 gameTime（含），0 表示无锁定。 */
    private var holdReleaseLockEndGameTime: Long = 0L

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(GoalkeeperRoleS2CPayload.TYPE) { payload, _ ->
            isGoalkeeper = payload.isGoalkeeper
            if (!isGoalkeeper) {
                isHoldingBall = false
                holdReleaseLockEndGameTime = 0L
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(GoalkeeperHoldLockS2CPayload.TYPE) { payload, _ ->
            applyHoldReleaseLock(payload.lockTicksRemaining)
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            updateHoldingState(client.player)
        }
    }

    fun isHoldReleaseLocked(): Boolean = holdReleaseLockRatio() > 0f

    /** 剩余锁定比例 1→0，用于 HUD 进度条。 */
    fun holdReleaseLockRatio(): Float {
        if (!isHoldingBall || GoalkeeperInputConfig.GK_HOLD_RELEASE_LOCK_TICKS <= 0) {
            return 0f
        }
        val level = Minecraft.getInstance().level ?: return 0f
        val remaining = (holdReleaseLockEndGameTime - level.gameTime).toFloat()
        if (remaining <= 0f) {
            return 0f
        }
        return (remaining / GoalkeeperInputConfig.GK_HOLD_RELEASE_LOCK_TICKS.toFloat()).coerceIn(0f, 1f)
    }

    private fun applyHoldReleaseLock(lockTicksRemaining: Int) {
        val level = Minecraft.getInstance().level
        holdReleaseLockEndGameTime = if (lockTicksRemaining <= 0 || level == null) {
            0L
        } else {
            level.gameTime + lockTicksRemaining
        }
    }

    private fun updateHoldingState(player: LocalPlayer?) {
        if (player == null || !isGoalkeeper) {
            isHoldingBall = false
            holdReleaseLockEndGameTime = 0L
            return
        }

        val holding = player.level().getEntitiesOfClass(
            Football::class.java,
            player.boundingBox.inflate(6.0),
        ).any { it.getHolderEntityId() == player.id }

        if (!holding) {
            holdReleaseLockEndGameTime = 0L
        }
        isHoldingBall = holding
    }
}
