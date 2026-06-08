package net.astrorbits.football.client

import net.astrorbits.football.Football
import net.astrorbits.football.client.key.FootballInputHandler
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.astrorbits.football.match.SetPieceKind
import net.astrorbits.football.network.GoalkeeperHoldActionPermissionsS2CPayload
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

    /** 释放锁定开始时的世界 gameTime。 */
    private var holdReleaseLockStartGameTime: Long = 0L

    /** 释放锁定结束时的世界 gameTime（不含），0 表示无锁定。 */
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

        ClientPlayNetworking.registerGlobalReceiver(GoalkeeperHoldActionPermissionsS2CPayload.TYPE) { payload, _ ->
            GoalkeeperHoldActionPermissionsClient.apply(
                canCatch = payload.canCatch,
                canDrop = payload.canDrop,
                canThrow = payload.canThrow,
            )
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            updateHoldingState(client.player)
        }
    }

    fun isHoldReleaseLocked(): Boolean = holdReleaseLockRatio() > 0f

    /** 剩余锁定比例 1→0（tick 精度，用于输入判定）。 */
    fun holdReleaseLockRatio(): Float = holdReleaseLockRatio(partialTick = 0f)

    fun holdReleaseLockRatio(partialTick: Float): Float {
        val level = Minecraft.getInstance().level ?: return 0f
        val total = holdReleaseLockEndGameTime - holdReleaseLockStartGameTime
        if (total <= 0L) {
            return 0f
        }
        val remaining = holdReleaseLockEndGameTime - level.gameTime - partialTick
        if (remaining <= 0f) {
            return 0f
        }
        return (remaining / total.toFloat()).coerceIn(0f, 1f)
    }

    /** 供 HUD 每帧采样（与服务端 tick 对齐，可用 partialTick 平滑）。 */
    fun liveHoldReleaseLockRatio(partialTick: Float = 0f): Float = holdReleaseLockRatio(partialTick)

    fun holdReleaseLockTotalTicks(): Int {
        val total = holdReleaseLockEndGameTime - holdReleaseLockStartGameTime
        return total.coerceAtLeast(1L).toInt()
    }

    private fun applyHoldReleaseLock(lockTicksRemaining: Int) {
        val level = Minecraft.getInstance().level ?: return
        if (lockTicksRemaining <= 0) {
            clearHoldReleaseLock()
            return
        }
        val now = level.gameTime
        holdReleaseLockStartGameTime = now
        holdReleaseLockEndGameTime = now + lockTicksRemaining
    }

    private fun updateHoldingState(player: LocalPlayer?) {
        if (player == null) {
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
            if (isGoalkeeper && !shouldDeferHoldLockPredictionForGoalKick(player)) {
                predictHoldReleaseLock(level.gameTime)
            }
            FootballInputHandler.onGoalkeeperBeganHoldingBall()
        }

        if (!holding && wasHoldingBall) {
            expireHoldReleaseLockIfNeeded()
        }

        wasHoldingBall = holding
        isHoldingBall = holding
    }

    /** 球门球捡球由服务端下发 [GOAL_KICK_HOLD_LOCK_TICKS] 窗口，避免与默认持球保护时长不同步。 */
    private fun shouldDeferHoldLockPredictionForGoalKick(player: LocalPlayer): Boolean {
        val localTeam = FootballOperabilityClient.resolveLocalPlayerTeam(player) ?: MatchStartClient.playerTeam
        return SetPieceClient.kind == SetPieceKind.GOAL_KICK &&
            SetPieceClient.restartTeam == localTeam
    }

    /** 鱼跃摘球等场景下实体同步可能晚于 S2C；仅在尚无服务端窗口时做短预测。 */
    private fun predictHoldReleaseLock(now: Long) {
        val ticks = GoalkeeperInputConfig.GK_HOLD_RELEASE_LOCK_TICKS
        if (ticks <= 0 || holdReleaseLockEndGameTime > now) {
            return
        }
        holdReleaseLockStartGameTime = now
        holdReleaseLockEndGameTime = now + ticks
    }

    private fun expireHoldReleaseLockIfNeeded() {
        val level = Minecraft.getInstance().level ?: return
        if (holdReleaseLockEndGameTime > 0L && level.gameTime >= holdReleaseLockEndGameTime) {
            clearHoldReleaseLock()
        }
    }

    private fun clearHoldReleaseLock() {
        holdReleaseLockStartGameTime = 0L
        holdReleaseLockEndGameTime = 0L
    }

    private fun resetHoldState() {
        isHoldingBall = false
        wasHoldingBall = false
        clearHoldReleaseLock()
    }

    /** 比赛重置：关闭场上门将操作，正式门将登记由服务端保留至下一场。 */
    fun onMatchReset() {
        isGoalkeeper = false
        resetHoldState()
        GoalkeeperHoldActionPermissionsClient.reset()
        GoalkeeperHoldStealProtectionClient.onMatchReset()
    }
}
