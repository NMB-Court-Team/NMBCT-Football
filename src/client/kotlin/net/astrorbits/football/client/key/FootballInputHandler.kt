package net.astrorbits.football.client.key

import net.astrorbits.football.client.FootballOperabilityClient
import net.astrorbits.football.client.GoalkeeperStateClient
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

    private var kickPressStartMs: Long? = null

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

    /** 基于实时时钟采样蓄力（供 HUD 每帧调用，避免仅 tick 更新导致进度条卡顿）。 */
    fun liveKickChargeDisplay(): KickChargeDisplayState? {
        val start = kickPressStartMs ?: return null
        if (!FootballKeyBindings.KICK.isDown) {
            return null
        }
        val chargingThrow = GoalkeeperStateClient.isGoalkeeper && GoalkeeperStateClient.isHoldingBall
        val chargingShoot = !GoalkeeperStateClient.isGoalkeeper
        if (!chargingThrow && !chargingShoot) {
            return null
        }
        val heldMs = System.currentTimeMillis() - start
        val settings = chargeSettings()
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
        handleKickLongPress(player, holdingBall = false)
        handleTrapPress(player, FootballActionType.TRAP)
        handleChipPress(player)
        handleDribbleHold(player)
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
            handleGoalkeeperDivePress(player)
            handleTrapPress(player, FootballActionType.GK_CATCH)
            handleChipPressGoalkeeper(player)
        }
    }

    /** 未持球时 R 键仅鱼跃：不蓄力，松开即扑出（长短按均可）。 */
    private fun handleGoalkeeperDivePress(player: LocalPlayer) {
        when (getKickLongPressState()) {
            LongPressState.STARTED -> {
                kickPressStartMs = null
                resetChargeDisplay()
            }
            LongPressState.BEING_PRESSED -> Unit
            LongPressState.FINISHED -> {
                sendAction(player, FootballActionType.GK_DIVE, 0f, 0L, buildDiveFlags(player))
                kickPressStartMs = null
                resetChargeDisplay()
            }
            LongPressState.NONE -> Unit
        }
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
                kickPressStartMs = System.currentTimeMillis()
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
                    val heldMs = System.currentTimeMillis() - start
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
        sendAction(player, FootballActionType.DRIBBLE_HOLD, 0f, 0L, buildFlags(player))
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

    private fun sendAction(
        player: LocalPlayer,
        action: FootballActionType,
        chargeRatio: Float,
        chargeHeldMs: Long,
        flags: Int,
    ) {
        ClientPlayNetworking.send(
            FootballActionC2SPayload(
                action = action,
                chargeRatio = chargeRatio,
                chargeHeldMs = chargeHeldMs,
                flags = flags,
                lookYaw = player.yHeadRot,
                lookPitch = player.xRot,
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
        resetChargeDisplay()
        dribbleTickCounter = 0
        dribbleResumeBlocked = false
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
