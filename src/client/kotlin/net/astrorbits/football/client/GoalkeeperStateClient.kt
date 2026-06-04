package net.astrorbits.football.client

import net.astrorbits.football.Football
import net.astrorbits.football.client.key.FootballInputHandler
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.astrorbits.football.network.GoalkeeperHoldLockS2CPayload
import net.astrorbits.football.network.GoalkeeperRoleS2CPayload
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer

object GoalkeeperStateClient {
    private const val MS_PER_TICK = 50L

    var isGoalkeeper: Boolean = false
        private set

    var isHoldingBall: Boolean = false
        private set

    /** 释放锁定结束时的世界 gameTime（含），0 表示无锁定。 */
    private var holdReleaseLockEndGameTime: Long = 0L

    /** 释放锁定结束时的墙钟时间（毫秒），供 HUD 每帧平滑采样。 */
    private var holdReleaseLockEndWallMs: Long = 0L

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

    /** 剩余锁定比例 1→0（tick 精度，用于输入判定）。 */
    fun holdReleaseLockRatio(): Float = holdReleaseLockRatio(partialTick = 0f)

    fun holdReleaseLockRatio(partialTick: Float): Float {
        if (!isGoalkeeper || GoalkeeperInputConfig.GK_HOLD_RELEASE_LOCK_TICKS <= 0) {
            return 0f
        }
        val level = Minecraft.getInstance().level ?: return 0f
        val remaining = holdReleaseLockEndGameTime - level.gameTime - partialTick
        if (remaining <= 0f) {
            return 0f
        }
        return (
            remaining / GoalkeeperInputConfig.GK_HOLD_RELEASE_LOCK_TICKS.toFloat()
            ).coerceIn(0f, 1f)
    }

    /** 基于墙钟的剩余锁定比例，供 HUD 每帧采样。 */
    fun liveHoldReleaseLockRatio(): Float {
        if (!isGoalkeeper || GoalkeeperInputConfig.GK_HOLD_RELEASE_LOCK_TICKS <= 0) {
            return 0f
        }
        val endWallMs = holdReleaseLockEndWallMs
        if (endWallMs <= 0L) {
            return 0f
        }
        val totalMs = GoalkeeperInputConfig.GK_HOLD_RELEASE_LOCK_TICKS * MS_PER_TICK
        val remainingMs = endWallMs - System.currentTimeMillis()
        if (remainingMs <= 0L) {
            return 0f
        }
        return (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    }

    private fun applyHoldReleaseLock(lockTicksRemaining: Int) {
        val level = Minecraft.getInstance().level ?: return
        if (lockTicksRemaining <= 0) {
            clearHoldReleaseLock()
            return
        }
        val endTime = level.gameTime + lockTicksRemaining
        if (endTime > holdReleaseLockEndGameTime) {
            holdReleaseLockEndGameTime = endTime
            holdReleaseLockEndWallMs = System.currentTimeMillis() + lockTicksRemaining * MS_PER_TICK
        }
    }

    private fun updateHoldingState(player: LocalPlayer?) {
        if (player == null || !isGoalkeeper) {
            resetHoldState()
            return
        }

        expireHoldReleaseLockIfNeeded()

        val level = player.level()
        val holding = level.getEntitiesOfClass(
            Football::class.java,
            player.boundingBox.inflate(6.0),
        ).any { it.getHolderEntityId() == player.id }

        if (holding && !wasHoldingBall) {
            predictHoldReleaseLock(level.gameTime)
            GoalkeeperHoldStealProtectionClient.onGoalkeeperBeganHolding(player, level.gameTime)
            FootballInputHandler.onGoalkeeperBeganHoldingBall()
        }

        if (!holding && wasHoldingBall) {
            expireHoldReleaseLockIfNeeded()
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
            holdReleaseLockEndWallMs = System.currentTimeMillis() +
                GoalkeeperInputConfig.GK_HOLD_RELEASE_LOCK_TICKS * MS_PER_TICK
        }
    }

    private fun expireHoldReleaseLockIfNeeded() {
        val level = Minecraft.getInstance().level
        val wallExpired = holdReleaseLockEndWallMs > 0L &&
            System.currentTimeMillis() >= holdReleaseLockEndWallMs
        val tickExpired = level != null &&
            holdReleaseLockEndGameTime > 0L &&
            level.gameTime >= holdReleaseLockEndGameTime
        if (wallExpired || tickExpired) {
            clearHoldReleaseLock()
        }
    }

    private fun clearHoldReleaseLock() {
        holdReleaseLockEndGameTime = 0L
        holdReleaseLockEndWallMs = 0L
    }

    private fun resetHoldState() {
        isHoldingBall = false
        wasHoldingBall = false
        clearHoldReleaseLock()
    }

    /** 比赛重置：与服务端 [PlayerRoleState.reset] 对齐，避免客户端仍按守门员且残留持球锁。 */
    fun onMatchReset() {
        isGoalkeeper = false
        resetHoldState()
        GoalkeeperHoldStealProtectionClient.onMatchReset()
    }
}
