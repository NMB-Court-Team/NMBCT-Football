package net.astrorbits.football.client

import net.astrorbits.football.client.key.FootballKeyBindings
import net.astrorbits.football.config.FootballConfigs
import net.astrorbits.football.config.client.BoostSprintInputMode
import net.astrorbits.football.network.BoostSprintToggleC2SPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.player.LocalPlayer

object BoostSprintClient {
    private var boostPrevTickPressed = false
    private var toggleActive = false
    private var holdSentActive = false

    fun registerTick() {
        // handled from FootballInputHandler
    }

    fun tick(player: LocalPlayer) {
        val mode = FootballConfigs.client.boostSprintInputMode
        val down = FootballKeyBindings.BOOST_SPRINT.isDown
        when (mode) {
            BoostSprintInputMode.TOGGLE -> {
                if (down && !boostPrevTickPressed && player.isSprinting) {
                    toggleActive = !toggleActive
                    sendEnabled(toggleActive && canKeepBoost(player))
                } else if (!player.isSprinting || staminaBlocksBoost(player)) {
                    if (toggleActive) {
                        toggleActive = false
                        sendEnabled(false)
                    }
                }
            }
            BoostSprintInputMode.HOLD -> {
                val want = down && player.isSprinting && canKeepBoost(player)
                if (want != holdSentActive) {
                    holdSentActive = want
                    sendEnabled(want)
                }
                if (!player.isSprinting && holdSentActive) {
                    holdSentActive = false
                    sendEnabled(false)
                }
            }
        }
        if (staminaBlocksBoost(player)) {
            toggleActive = false
            holdSentActive = false
        }
        boostPrevTickPressed = down
    }

    fun reset() {
        boostPrevTickPressed = false
        toggleActive = false
        holdSentActive = false
    }

    fun onServerDeactivated() {
        toggleActive = false
        holdSentActive = false
    }

    private fun canKeepBoost(player: LocalPlayer): Boolean =
        player.isSprinting && !staminaBlocksBoost(player)

    private fun staminaBlocksBoost(player: LocalPlayer): Boolean =
        !player.isCreative && StaminaClient.stamina <= 0f

    private fun sendEnabled(enabled: Boolean) {
        if (!ClientPlayNetworking.canSend(BoostSprintToggleC2SPayload.TYPE)) {
            return
        }
        ClientPlayNetworking.send(BoostSprintToggleC2SPayload(enabled))
    }
}
