package net.astrorbits.football.client.key

import net.astrorbits.football.client.FootballOperabilityClient
import net.astrorbits.football.client.GoalkeeperStateClient
import net.astrorbits.football.client.SlideTackleStateClient
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.config.FootballConfigs
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.input.FootballMovementInputUtil
import net.astrorbits.football.network.FootballActionC2SPayload
import net.astrorbits.football.network.FootballActionType
import net.astrorbits.football.util.KickChargeUtil
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.player.LocalPlayer

object FootballInputHandler {
    private var kickPrevTickPressed = false
    private var trapPrevTickPressed = false
    private var chipPrevTickPressed = false
    private var dribblePrevTickPressed = false
    private var slidePrevTickPressed = false

    private var kickPressStartMs: Long? = null
    /** 每帧捕获的抬键时长，避免仅在 tick 采样导致 1~2 tick 偏差。 */
    private var kickReleaseHeldMsOverride: Long? = null
    private var kickRealtimePrevDown = false
    /** 按带球键打断鱼跃蓄力后，忽略本次 R 键松开触发的鱼跃。 */
    private var diveChargeCancelled = false

    var shootChargeRatio: Float = 0f
        private set
    var isChargingShoot: Boolean = false
        private set
    var shootChargePhase: KickChargeUtil.Phase = KickChargeUtil.Phase.NONE
        private set

    var throwChargeRatio: Float = 0f
        private set
    var isChargingThrow: Boolean = false
        private set
    var throwChargePhase: KickChargeUtil.Phase = KickChargeUtil.Phase.NONE
        private set

    data class KickChargeDisplayState(
        val ratio: Float,
        val phase: KickChargeUtil.Phase,
    )

    /** 未持球守门员正按住 R 鱼跃蓄力且尚未被带球键打断（供 HUD 提示用）。 */
    fun isGoalkeeperDiveChargeActive(): Boolean {
        syncKickPressRealtimeClock()
        return GoalkeeperStateClient.isGoalkeeper &&
            !GoalkeeperStateClient.isHoldingBall &&
            FootballKeyBindings.KICK.isDown &&
            kickPressStartMs != null &&
            !diveChargeCancelled
    }

    /** 基于实时时钟采样蓄力（供 HUD 每帧调用，避免仅 tick 更新导致进度条卡顿）。 */
    fun liveKickChargeDisplay(): KickChargeDisplayState? {
        if (MatchStartClient.isLocked) return null
        syncKickPressRealtimeClock()
        val start = kickPressStartMs ?: return null
        if (!FootballKeyBindings.KICK.isDown) {
            return null
        }
        val heldMs = System.currentTimeMillis() - start
        val settings = chargeSettings()
        val chargingDive = GoalkeeperStateClient.isGoalkeeper && !GoalkeeperStateClient.isHoldingBall
        val chargingThrow = GoalkeeperStateClient.isGoalkeeper && GoalkeeperStateClient.isHoldingBall
        val chargingShoot = !GoalkeeperStateClient.isGoalkeeper
        if (chargingDive) {
            if (!KickChargeUtil.isLinearCharging(heldMs, settings)) {
                return null
            }
            return KickChargeDisplayState(
                ratio = KickChargeUtil.computeLinearRatio(heldMs, settings),
                phase = KickChargeUtil.Phase.RISING,
            )
        }
        if (!chargingThrow && !chargingShoot) {
            return null
        }
        val phase = KickChargeUtil.computePhase(heldMs, settings)
        if (phase == KickChargeUtil.Phase.NONE) {
            return null
        }
        return KickChargeDisplayState(
            ratio = KickChargeUtil.computeRatio(heldMs, settings),
            phase = phase,
        )
    }

    private var dribbleTickCounter = 0
    /** 传球/射门/挑球后须松开带球键再按，避免仍按住时立刻重新拉球。 */
    private var dribbleResumeBlocked = false

    fun registerTickEvent() {
        ClientTickEvents.END_CLIENT_TICK.register reg@{ client ->
            val player = client.player
            val level = client.level
            if (player == null || level == null) {
                resetTransientState(notifyDribbleEnd = false)
                return@reg
            }

            if (client.screen != null || client.isPaused) {
                resetTransientState(player)
                updatePrevTickPressed()
                return@reg
            }

            if (MatchStartClient.isLocked) {
                handleSlideTacklePress(player)
                updatePrevTickPressed()
                return@reg
            }

            if (!player.mainHandItem.isEmpty) {
                resetTransientState(player)
                updatePrevTickPressed()
                return@reg
            }

            try {
                if (GoalkeeperStateClient.isGoalkeeper) {
                    handleGoalkeeperInput(player)
                } else {
                    handleOutfieldInput(player)
                }
            } finally {
                updatePrevTickPressed()
            }
        }
    }

    fun sendItemThrow(player: LocalPlayer) {
        sendAction(player, FootballActionType.ITEM_THROW, 0f, 0L, 0)
    }

    private fun handleOutfieldInput(player: LocalPlayer) {
        if (SlideTackleStateClient.isSliding(player.id)) {
            // 滑铲时仅允许维持/结束滑铲输入，不发送其他主动球类动作。
            kickPressStartMs = null
            resetChargeDisplay()
            dribbleTickCounter = 0
            handleSlideTacklePress(player)
            return
        }
        handleKickLongPress(player, holdingBall = false)
        handleTrapPress(player, FootballActionType.TRAP)
        handleChipPress(player)
        handleDribbleHold(player)
        handleSlideTacklePress(player)
    }

    fun onGoalkeeperBeganHoldingBall() {
        kickPressStartMs = null
        resetChargeDisplay()
    }

    private fun handleGoalkeeperInput(player: LocalPlayer) {
        val holdingBall = GoalkeeperStateClient.isHoldingBall
        val releaseLocked = holdingBall && GoalkeeperStateClient.isHoldReleaseLocked()
        if (holdingBall) {
            if (!releaseLocked) {
                handleKickLongPress(player, holdingBall = true)
                handleTrapPress(player, FootballActionType.GK_DROP)
            } else {
                handleKickLongPressBlocked()
            }
        } else {
            tryInterruptDiveCharge()
            handleGoalkeeperDivePress(player)
            handleTrapPress(player, FootballActionType.GK_CATCH)
            handleChipPressGoalkeeper(player)
        }
    }

    /** 未持球时 R 键鱼跃：支持按住蓄力，松开后执行。 */
    private fun handleGoalkeeperDivePress(player: LocalPlayer) {
        when (getKickLongPressState()) {
            LongPressState.STARTED -> {
                diveChargeCancelled = false
                if (kickPressStartMs == null) {
                    kickPressStartMs = System.currentTimeMillis()
                }
            }
            LongPressState.BEING_PRESSED -> Unit
            LongPressState.FINISHED -> {
                val heldMs = kickReleaseHeldMsOverride ?: kickPressStartMs?.let { System.currentTimeMillis() - it } ?: 0L
                kickReleaseHeldMsOverride = null
                kickPressStartMs = null
                resetChargeDisplay()
                if (diveChargeCancelled) {
                    diveChargeCancelled = false
                    return
                }
                val settings = chargeSettings()
                val chargeRatio = KickChargeUtil.computeLinearRatio(heldMs.coerceAtLeast(0L), settings)
                sendAction(player, FootballActionType.GK_DIVE, chargeRatio, heldMs.coerceAtLeast(0L), buildDiveFlags(player))
            }
            LongPressState.NONE -> Unit
        }
    }

    /** 鱼跃蓄力中按带球键（与场员相同键位）取消蓄力，不触发带球逻辑。 */
    private fun tryInterruptDiveCharge() {
        if (kickPressStartMs == null || !FootballKeyBindings.KICK.isDown) {
            return
        }
        if (FootballKeyBindings.DRIBBLE.isDown && !dribblePrevTickPressed) {
            cancelDiveCharge()
        }
    }

    private fun cancelDiveCharge() {
        diveChargeCancelled = true
        kickPressStartMs = null
        kickReleaseHeldMsOverride = null
        resetChargeDisplay()
    }

    /** 持球保护期间忽略开球/放下输入，并清除蓄力显示。 */
    private fun handleKickLongPressBlocked() {
        when (getKickLongPressState()) {
            LongPressState.FINISHED -> {
                kickPressStartMs = null
                resetChargeDisplay()
            }
            LongPressState.NONE -> Unit
            else -> Unit
        }
    }

    private fun handleKickLongPress(player: LocalPlayer, holdingBall: Boolean) {
        when (getKickLongPressState()) {
            LongPressState.STARTED -> {
                // 若已在渲染帧捕获按下时刻，则保持它，避免起始时间晚 1 tick。
                if (kickPressStartMs == null) {
                    kickPressStartMs = System.currentTimeMillis()
                }
                if (holdingBall) {
                    isChargingThrow = false
                    throwChargeRatio = 0f
                    throwChargePhase = KickChargeUtil.Phase.NONE
                } else {
                    isChargingShoot = false
                    shootChargeRatio = 0f
                    shootChargePhase = KickChargeUtil.Phase.NONE
                }
            }
            LongPressState.BEING_PRESSED -> {
                if (!canChargeKick(player, holdingBall)) {
                    cancelKickCharge()
                    return
                }
                val start = kickPressStartMs ?: return
                val heldMs = System.currentTimeMillis() - start
                if (holdingBall) {
                    updateThrowCharge(heldMs)
                } else if (!GoalkeeperStateClient.isGoalkeeper) {
                    updateShootCharge(heldMs)
                }
            }
            LongPressState.FINISHED -> {
                val start = kickPressStartMs
                if (start != null) {
                    val heldMs = kickReleaseHeldMsOverride ?: (System.currentTimeMillis() - start)
                    kickReleaseHeldMsOverride = null
                    val flags = buildFlags(player)
                    when {
                        holdingBall && GoalkeeperStateClient.isHoldReleaseLocked() -> Unit
                        holdingBall && heldMs < FootballInputConfig.TAP_MAX_MS ->
                            sendAction(player, FootballActionType.GK_THROW_SHORT, 0f, heldMs, flags)
                        holdingBall && KickChargeUtil.isCharging(heldMs, chargeSettings()) ->
                            sendAction(
                                player,
                                FootballActionType.GK_THROW_LONG,
                                throwChargeRatio,
                                heldMs,
                                flags,
                            )
                        holdingBall ->
                            sendAction(player, FootballActionType.GK_THROW_SHORT, 0f, heldMs, flags)
                        heldMs < FootballInputConfig.TAP_MAX_MS -> {
                            sendAction(player, FootballActionType.PASS, 0f, heldMs, flags)
                            blockDribbleResume(player)
                        }
                        KickChargeUtil.isCharging(heldMs, chargeSettings()) -> {
                            sendAction(player, FootballActionType.SHOOT, shootChargeRatio, heldMs, flags)
                            blockDribbleResume(player)
                        }
                        else -> {
                            sendAction(player, FootballActionType.PASS, 0f, heldMs, flags)
                            blockDribbleResume(player)
                        }
                    }
                }
                kickPressStartMs = null
                resetChargeDisplay()
            }
            LongPressState.NONE -> Unit
        }
    }

    private fun canChargeKick(player: LocalPlayer, holdingBall: Boolean): Boolean {
        if (holdingBall) {
            return !GoalkeeperStateClient.isHoldReleaseLocked()
        }
        return FootballOperabilityClient.canOperateFootball(player, player.level())
    }

    private fun cancelKickCharge() {
        kickPressStartMs = null
        kickReleaseHeldMsOverride = null
        resetChargeDisplay()
    }

    private fun handleTrapPress(player: LocalPlayer, action: FootballActionType) {
        if (FootballKeyBindings.TRAP.isDown && !trapPrevTickPressed) {
            sendAction(player, action, 0f, 0L, 0)
        }
    }

    private fun handleChipPress(player: LocalPlayer) {
        if (FootballKeyBindings.CHIP.isDown && !chipPrevTickPressed) {
            sendAction(player, FootballActionType.CHIP, 0f, 0L, buildFlags(player))
            blockDribbleResume(player)
        }
    }

    private fun handleChipPressGoalkeeper(player: LocalPlayer) {
        if (FootballKeyBindings.CHIP.isDown && !chipPrevTickPressed) {
            sendAction(player, FootballActionType.GK_PUNCH, 0f, 0L, 0)
        }
    }

    private fun handleDribbleHold(player: LocalPlayer) {
        if (!FootballKeyBindings.DRIBBLE.isDown) {
            dribbleResumeBlocked = false
            if (dribblePrevTickPressed) {
                sendAction(player, FootballActionType.DRIBBLE_END, 0f, 0L, 0)
            }
            dribbleTickCounter = 0
            return
        }

        if (dribbleResumeBlocked) {
            return
        }

        if (!FootballMovementInputUtil.hasMovementInput(player, LookAroundClient.movementYaw(player))) {
            return
        }

        if (getDribbleLongPressState() == LongPressState.STARTED) {
            dribbleTickCounter = FootballInputConfig.DRIBBLE_HOLD_PACKET_INTERVAL
        }

        dribbleTickCounter++
        if (dribbleTickCounter < FootballInputConfig.DRIBBLE_HOLD_PACKET_INTERVAL) {
            return
        }
        dribbleTickCounter = 0
        sendDribbleAction(player, FootballActionType.DRIBBLE_HOLD, buildDribbleFlags(player))
    }

    private fun handleSlideTacklePress(player: LocalPlayer) {
        val down = FootballKeyBindings.SLIDE_TACKLE.isDown
        if (down && !slidePrevTickPressed && player.isSprinting) {
            sendAction(player, FootballActionType.SLIDE_TACKLE, 0f, 0L, buildFlags(player))
            return
        }
        if (!down && slidePrevTickPressed) {
            sendAction(player, FootballActionType.SLIDE_TACKLE_END, 0f, 0L, 0)
        }
    }

    private fun updateShootCharge(heldMs: Long) {
        val settings = chargeSettings()
        val phase = KickChargeUtil.computePhase(heldMs, settings)
        shootChargePhase = phase
        if (phase == KickChargeUtil.Phase.NONE) {
            isChargingShoot = false
            shootChargeRatio = 0f
            return
        }
        isChargingShoot = true
        shootChargeRatio = KickChargeUtil.computeRatio(heldMs, settings)
    }

    private fun updateThrowCharge(heldMs: Long) {
        val settings = chargeSettings()
        val phase = KickChargeUtil.computePhase(heldMs, settings)
        throwChargePhase = phase
        if (phase == KickChargeUtil.Phase.NONE) {
            isChargingThrow = false
            throwChargeRatio = 0f
            return
        }
        isChargingThrow = true
        throwChargeRatio = KickChargeUtil.computeRatio(heldMs, settings)
    }

    private fun chargeSettings() = FootballConfigs.server.playerInput.charge

    private fun getKickLongPressState(): LongPressState {
        val key = FootballKeyBindings.KICK
        if (key.isDown && !kickPrevTickPressed) return LongPressState.STARTED
        if (key.isDown && kickPrevTickPressed) return LongPressState.BEING_PRESSED
        if (!key.isDown && kickPrevTickPressed) return LongPressState.FINISHED
        return LongPressState.NONE
    }

    private fun getDribbleLongPressState(): LongPressState {
        val key = FootballKeyBindings.DRIBBLE
        if (key.isDown && !dribblePrevTickPressed) return LongPressState.STARTED
        if (key.isDown && dribblePrevTickPressed) return LongPressState.BEING_PRESSED
        if (!key.isDown && dribblePrevTickPressed) return LongPressState.FINISHED
        return LongPressState.NONE
    }

    private fun updatePrevTickPressed() {
        kickPrevTickPressed = FootballKeyBindings.KICK.isDown
        trapPrevTickPressed = FootballKeyBindings.TRAP.isDown
        chipPrevTickPressed = FootballKeyBindings.CHIP.isDown
        dribblePrevTickPressed = FootballKeyBindings.DRIBBLE.isDown
        slidePrevTickPressed = FootballKeyBindings.SLIDE_TACKLE.isDown
    }

    private fun buildFlags(player: LocalPlayer): Int {
        var flags = 0
        if (player.isSprinting) {
            flags = flags or FootballInputConfig.FLAG_SPRINT
        }
        return flags
    }

    private fun buildDiveFlags(player: LocalPlayer): Int {
        var flags = 0
        if (!FootballMovementInputUtil.hasMovementInput(player, LookAroundClient.movementYaw(player))) {
            flags = flags or FootballInputConfig.FLAG_DIVE_USE_LOOK
        }
        return flags
    }

    private fun buildDribbleFlags(player: LocalPlayer): Int {
        var flags = buildFlags(player)
        if (LookAroundClient.active) {
            flags = flags or FootballInputConfig.FLAG_LOOK_AROUND
        }
        return flags
    }

    private fun sendDribbleAction(player: LocalPlayer, action: FootballActionType, flags: Int) {
        val lookYaw = if (LookAroundClient.active) {
            LookAroundClient.movementYaw(player)
        } else {
            player.yHeadRot
        }
        sendAction(
            player = player,
            action = action,
            chargeRatio = 0f,
            chargeHeldMs = 0L,
            flags = flags,
            lookYaw = lookYaw,
            lookPitch = player.xRot,
        )
    }

    private fun sendAction(
        player: LocalPlayer,
        action: FootballActionType,
        chargeRatio: Float,
        chargeHeldMs: Long,
        flags: Int,
        lookYaw: Float = player.yHeadRot,
        lookPitch: Float = player.xRot,
    ) {
        ClientPlayNetworking.send(
            FootballActionC2SPayload(
                action = action,
                chargeRatio = chargeRatio,
                chargeHeldMs = chargeHeldMs,
                flags = flags,
                lookYaw = lookYaw,
                lookPitch = lookPitch,
            )
        )
    }

    private fun blockDribbleResume(player: LocalPlayer) {
        dribbleResumeBlocked = true
        sendAction(player, FootballActionType.DRIBBLE_END, 0f, 0L, 0)
        dribbleTickCounter = 0
    }

    private fun resetTransientState(player: LocalPlayer? = null, notifyDribbleEnd: Boolean = true) {
        if (notifyDribbleEnd && player != null && !GoalkeeperStateClient.isGoalkeeper &&
            (dribblePrevTickPressed || FootballKeyBindings.DRIBBLE.isDown)
        ) {
            sendAction(player, FootballActionType.DRIBBLE_END, 0f, 0L, 0)
        }
        kickPressStartMs = null
        kickReleaseHeldMsOverride = null
        diveChargeCancelled = false
        resetChargeDisplay()
        dribbleTickCounter = 0
        dribbleResumeBlocked = false
    }

    private fun syncKickPressRealtimeClock() {
        val down = FootballKeyBindings.KICK.isDown
        val now = System.currentTimeMillis()
        val chargingDive = GoalkeeperStateClient.isGoalkeeper && !GoalkeeperStateClient.isHoldingBall
        val chargingThrow = GoalkeeperStateClient.isGoalkeeper && GoalkeeperStateClient.isHoldingBall
        val chargingShoot = !GoalkeeperStateClient.isGoalkeeper
        val canTrackCharge = chargingDive || chargingThrow || chargingShoot

        if (down && !kickRealtimePrevDown && canTrackCharge && kickPressStartMs == null) {
            kickPressStartMs = now
            kickReleaseHeldMsOverride = null
        } else if (!down && kickRealtimePrevDown) {
            val start = kickPressStartMs
            if (start != null) {
                kickReleaseHeldMsOverride = (now - start).coerceAtLeast(0L)
            }
        }
        kickRealtimePrevDown = down
    }

    private fun resetChargeDisplay() {
        isChargingShoot = false
        shootChargeRatio = 0f
        shootChargePhase = KickChargeUtil.Phase.NONE
        isChargingThrow = false
        throwChargeRatio = 0f
        throwChargePhase = KickChargeUtil.Phase.NONE
    }

    private enum class LongPressState {
        STARTED,
        BEING_PRESSED,
        FINISHED,
        NONE
    }
}
