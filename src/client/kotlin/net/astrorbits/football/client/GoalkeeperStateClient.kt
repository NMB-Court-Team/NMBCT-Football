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

    private var wasHoldingBall: Boolean = false

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(GoalkeeperRoleS2CPayload.TYPE) { payload, _ ->
            isGoalkeeper = payload.isGoalkeeper
            if (!isGoalkeeper) {
                resetHoldState()
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
        if (!isGoalkeeper || GoalkeeperInputConfig.GK_HOLD_RELEASE_LOCK_TICKS <= 0) {
            return 0f
        }
        val level = Minecraft.getInstance().level ?: return 0f
        val remaining = holdReleaseLockEndGameTime - level.gameTime
        if (remaining <= 0L) {
            return 0f
        }
        return (
            remaining.toFloat() / GoalkeeperInputConfig.GK_HOLD_RELEASE_LOCK_TICKS.toFloat()
            ).coerceIn(0f, 1f)
    }

    private fun applyHoldReleaseLock(lockTicksRemaining: Int) {
        val level = Minecraft.getInstance().level ?: return
        if (lockTicksRemaining <= 0) {
            holdReleaseLockEndGameTime = 0L
            return
        }
        val endTime = level.gameTime + lockTicksRemaining
        if (endTime > holdReleaseLockEndGameTime) {
            holdReleaseLockEndGameTime = endTime
        }
    }

    private fun updateHoldingState(player: LocalPlayer?) {
        if (player == null || !isGoalkeeper) {
            resetHoldState()
            return
        }

        val level = player.level()
        val holding = level.getEntitiesOfClass(
            Football::class.java,
            player.boundingBox.inflate(6.0),
        ).any { it.getHolderEntityId() == player.id }

        if (holding && !wasHoldingBall) {
            predictHoldReleaseLock(level.gameTime)
            FootballInputHandler.onGoalkeeperBeganHoldingBall()
        }

        if (!holding && wasHoldingBall && level.gameTime >= holdReleaseLockEndGameTime) {
            holdReleaseLockEndGameTime = 0L
        }

        wasHoldingBall = holding
        isHoldingBall = holding
    }

    /** 鱼跃摘球等场景下实体同步可能晚于 S2C，本地先预测锁定避免保护被误清。 */
    private fun predictHoldReleaseLock(now: Long) {
        if (GoalkeeperInputConfig.GK_HOLD_RELEASE_LOCK_TICKS <= 0) {
            return
        }
        val predictedEnd = now + GoalkeeperInputConfig.GK_HOLD_RELEASE_LOCK_TICKS
        if (predictedEnd > holdReleaseLockEndGameTime) {
            holdReleaseLockEndGameTime = predictedEnd
        }
    }

    private fun resetHoldState() {
        isHoldingBall = false
        wasHoldingBall = false
        holdReleaseLockEndGameTime = 0L
    }
}
