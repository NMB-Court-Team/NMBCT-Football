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

    private var dribbleTickCounter = 0

    fun registerTickEvent() {
        ClientTickEvents.END_CLIENT_TICK.register reg@{ client ->
            val player = client.player
            val level = client.level
            if (player == null || level == null) {
                resetTransientState()
                return@reg
            }

            if (client.screen != null || client.isPaused) {
                resetTransientState()
                updatePrevTickPressed()
                return@reg
            }

            if (!player.mainHandItem.isEmpty) {
                resetTransientState()
                updatePrevTickPressed()
                return@reg
            }

            try {
                handleKickLongPress(player)
                handleTrapPress()
                handleChipPress(player)
                handleDribbleHold(player)
            } finally {
                updatePrevTickPressed()
            }
        }
    }

    private fun handleKickLongPress(player: LocalPlayer) {
        when (getKickLongPressState()) {
            LongPressState.STARTED -> {
                kickPressStartMs = System.currentTimeMillis()
                isChargingShoot = false
                shootChargeRatio = 0f
            }
            LongPressState.BEING_PRESSED -> {
                val start = kickPressStartMs ?: return
                val heldMs = System.currentTimeMillis() - start
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
            LongPressState.FINISHED -> {
                val start = kickPressStartMs
                if (start != null) {
                    val heldMs = System.currentTimeMillis() - start
                    val flags = buildFlags(player)
                    when {
                        heldMs < FootballInputConfig.TAP_MAX_MS ->
                            sendAction(FootballActionType.PASS, 0f, flags)
                        heldMs >= FootballInputConfig.CHARGE_MIN_MS ->
                            sendAction(FootballActionType.SHOOT, shootChargeRatio, flags)
                        else ->
                            sendAction(FootballActionType.PASS, 0f, flags)
                    }
                }
                kickPressStartMs = null
                resetKickChargeDisplay()
            }
            LongPressState.NONE -> Unit
        }
    }

    private fun handleTrapPress() {
        if (FootballKeyBindings.TRAP.isDown && !trapPrevTickPressed) {
            sendAction(FootballActionType.TRAP, 0f, 0)
        }
    }

    private fun handleChipPress(player: LocalPlayer) {
        if (FootballKeyBindings.CHIP.isDown && !chipPrevTickPressed) {
            sendAction(FootballActionType.CHIP, 0f, buildFlags(player))
        }
    }

    private fun handleDribbleHold(player: LocalPlayer) {
        if (!FootballKeyBindings.DRIBBLE.isDown) {
            dribbleTickCounter = 0
            return
        }

        if (!FootballMovementInputUtil.hasMovementInput(player)) {
            return
        }

        if (getDribbleLongPressState() == LongPressState.STARTED) {
            dribbleTickCounter = FootballInputConfig.DRIBBLE_INTERVAL_TICKS
        }

        dribbleTickCounter++
        if (dribbleTickCounter < FootballInputConfig.DRIBBLE_INTERVAL_TICKS) {
            return
        }
        dribbleTickCounter = 0
        sendAction(FootballActionType.DRIBBLE, 0f, buildFlags(player))
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

    private fun sendAction(action: FootballActionType, chargeRatio: Float, flags: Int) {
        ClientPlayNetworking.send(
            FootballActionC2SPayload(
                action = action,
                chargeRatio = chargeRatio,
                flags = flags
            )
        )
    }

    private fun resetTransientState() {
        kickPressStartMs = null
        resetKickChargeDisplay()
        dribbleTickCounter = 0
    }

    private fun resetKickChargeDisplay() {
        isChargingShoot = false
        shootChargeRatio = 0f
    }

    private enum class LongPressState {
        STARTED,
        BEING_PRESSED,
        FINISHED,
        NONE
    }
}
