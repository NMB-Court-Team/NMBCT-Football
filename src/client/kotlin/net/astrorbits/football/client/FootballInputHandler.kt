package net.astrorbits.football.client

import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.input.FootballMovementInputUtil
import net.astrorbits.football.network.FootballActionC2SPayload
import net.astrorbits.football.network.FootballActionType
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

    var throwChargeRatio: Float = 0f
        private set
    var isChargingThrow: Boolean = false
        private set

    private var dribbleTickCounter = 0

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

    private fun handleOutfieldInput(player: LocalPlayer) {
        handleKickLongPress(player, holdingBall = false)
        handleTrapPress(player, FootballActionType.TRAP)
        handleChipPress(player)
        handleDribbleHold(player)
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
            handleKickLongPress(player, holdingBall = false)
            handleTrapPress(player, FootballActionType.GK_CATCH)
            handleChipPressGoalkeeper(player)
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
                } else {
                    isChargingShoot = false
                    shootChargeRatio = 0f
                }
            }
            LongPressState.BEING_PRESSED -> {
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
                        holdingBall && heldMs < FootballInputConfig.TAP_MAX_MS ->
                            sendAction(player, FootballActionType.GK_THROW_SHORT, 0f, flags)
                        holdingBall && heldMs >= FootballInputConfig.CHARGE_MIN_MS ->
                            sendAction(player, FootballActionType.GK_THROW_LONG, throwChargeRatio, flags)
                        holdingBall ->
                            sendAction(player, FootballActionType.GK_THROW_SHORT, 0f, flags)
                        GoalkeeperStateClient.isGoalkeeper && heldMs < FootballInputConfig.TAP_MAX_MS ->
                            sendAction(player, FootballActionType.GK_DIVE, 0f, buildDiveFlags(player))
                        GoalkeeperStateClient.isGoalkeeper ->
                            Unit
                        heldMs < FootballInputConfig.TAP_MAX_MS ->
                            sendAction(player, FootballActionType.PASS, 0f, flags)
                        heldMs >= FootballInputConfig.CHARGE_MIN_MS ->
                            sendAction(player, FootballActionType.SHOOT, shootChargeRatio, flags)
                        else ->
                            sendAction(player, FootballActionType.PASS, 0f, flags)
                    }
                }
                kickPressStartMs = null
                resetChargeDisplay()
            }
            LongPressState.NONE -> Unit
        }
    }

    private fun handleTrapPress(player: LocalPlayer, action: FootballActionType) {
        if (FootballKeyBindings.TRAP.isDown && !trapPrevTickPressed) {
            sendAction(player, action, 0f, 0)
        }
    }

    private fun handleChipPress(player: LocalPlayer) {
        if (FootballKeyBindings.CHIP.isDown && !chipPrevTickPressed) {
            sendAction(player, FootballActionType.CHIP, 0f, buildFlags(player))
        }
    }

    private fun handleChipPressGoalkeeper(player: LocalPlayer) {
        if (FootballKeyBindings.CHIP.isDown && !chipPrevTickPressed) {
            sendAction(player, FootballActionType.GK_PUNCH, 0f, 0)
        }
    }

    private fun handleDribbleHold(player: LocalPlayer) {
        if (!FootballKeyBindings.DRIBBLE.isDown) {
            if (dribblePrevTickPressed) {
                sendAction(player, FootballActionType.DRIBBLE_END, 0f, 0)
            }
            dribbleTickCounter = 0
            return
        }

        if (!FootballMovementInputUtil.hasMovementInput(player)) {
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
        sendAction(player, FootballActionType.DRIBBLE_HOLD, 0f, buildFlags(player))
    }

    private fun updateShootCharge(heldMs: Long) {
        if (heldMs >= FootballInputConfig.CHARGE_MIN_MS) {
            isChargingShoot = true
            val chargeMs = (heldMs - FootballInputConfig.CHARGE_MIN_MS)
                .coerceAtMost(FootballInputConfig.CHARGE_MAX_MS - FootballInputConfig.CHARGE_MIN_MS)
            shootChargeRatio = (
                chargeMs.toFloat() /
                    (FootballInputConfig.CHARGE_MAX_MS - FootballInputConfig.CHARGE_MIN_MS).toFloat()
                ).coerceIn(0f, 1f)
        } else {
            isChargingShoot = false
            shootChargeRatio = 0f
        }
    }

    private fun updateThrowCharge(heldMs: Long) {
        if (heldMs >= FootballInputConfig.CHARGE_MIN_MS) {
            isChargingThrow = true
            val chargeMs = (heldMs - FootballInputConfig.CHARGE_MIN_MS)
                .coerceAtMost(FootballInputConfig.CHARGE_MAX_MS - FootballInputConfig.CHARGE_MIN_MS)
            throwChargeRatio = (
                chargeMs.toFloat() /
                    (FootballInputConfig.CHARGE_MAX_MS - FootballInputConfig.CHARGE_MIN_MS).toFloat()
                ).coerceIn(0f, 1f)
        } else {
            isChargingThrow = false
            throwChargeRatio = 0f
        }
    }

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
        if (!FootballMovementInputUtil.hasMovementInput(player)) {
            flags = flags or FootballInputConfig.FLAG_DIVE_USE_LOOK
        }
        return flags
    }

    private fun sendAction(player: LocalPlayer, action: FootballActionType, chargeRatio: Float, flags: Int) {
        ClientPlayNetworking.send(
            FootballActionC2SPayload(
                action = action,
                chargeRatio = chargeRatio,
                flags = flags,
                lookYaw = player.yHeadRot,
                lookPitch = player.xRot,
            )
        )
    }

    private fun resetTransientState(player: LocalPlayer? = null, notifyDribbleEnd: Boolean = true) {
        if (notifyDribbleEnd && player != null && !GoalkeeperStateClient.isGoalkeeper &&
            (dribblePrevTickPressed || FootballKeyBindings.DRIBBLE.isDown)
        ) {
            sendAction(player, FootballActionType.DRIBBLE_END, 0f, 0)
        }
        kickPressStartMs = null
        resetChargeDisplay()
        dribbleTickCounter = 0
    }

    private fun resetChargeDisplay() {
        isChargingShoot = false
        shootChargeRatio = 0f
        isChargingThrow = false
        throwChargeRatio = 0f
    }

    private enum class LongPressState {
        STARTED,
        BEING_PRESSED,
        FINISHED,
        NONE
    }
}
