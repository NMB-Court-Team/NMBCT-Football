package net.astrorbits.football.client.key

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player

object LookAroundClient {
    val active: Boolean
        get() = lockedYaw != null

    private var lockedYaw: Float? = null
    private var lockedPitch: Float? = null
    private var prevKeyDown = false

    private var freeLookYaw = 0f
    private var freeLookPitch = 0f

    fun movementYaw(player: Player): Float = lockedYaw ?: player.yRot

    @JvmStatic
    fun onTurnApplied(player: LocalPlayer) {
        if (!active) {
            return
        }
        clampFreeLookYaw(player)
    }

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player
            if (player == null) {
                clearState()
                return@register
            }

            if (client.screen != null || client.isPaused) {
                if (active) {
                    restoreLockedView(player)
                }
                clearState()
                return@register
            }

            val keyDown = FootballKeyBindings.LOOK_AROUND.isDown
            when {
                keyDown && !prevKeyDown -> beginLookAround(player)
                !keyDown && prevKeyDown && active -> {
                    restoreLockedView(player)
                    clearState()
                }
            }
            prevKeyDown = keyDown
        }
    }

    @JvmStatic
    fun onAiStepHead(player: LocalPlayer) {
        if (!active) {
            return
        }
        freeLookYaw = player.yRot
        freeLookPitch = player.xRot
        val yaw = lockedYaw ?: return
        player.yRot = yaw
        player.yBodyRot = yaw
    }

    @JvmStatic
    fun onAiStepReturn(player: LocalPlayer) {
        if (!active) {
            return
        }
        player.yRot = freeLookYaw
        player.yHeadRot = freeLookYaw
        player.yBodyRot = lockedYaw ?: player.yBodyRot
        player.xRot = freeLookPitch
    }

    private fun beginLookAround(player: LocalPlayer) {
        lockedYaw = player.yRot
        lockedPitch = player.xRot
    }

    private fun restoreLockedView(player: LocalPlayer) {
        val yaw = lockedYaw ?: return
        val pitch = lockedPitch ?: player.xRot
        player.yRot = yaw
        player.yRotO = yaw
        player.yHeadRot = yaw
        player.yHeadRotO = yaw
        player.yBodyRot = yaw
        player.yBodyRotO = yaw
        player.xRot = pitch
        player.xRotO = pitch
    }

    private fun clearState() {
        lockedYaw = null
        lockedPitch = null
        prevKeyDown = false
    }

    private fun clampFreeLookYaw(player: LocalPlayer) {
        val base = lockedYaw ?: return
        val offset = Mth.wrapDegrees(player.yRot - base)
        val clampedYaw = base + offset.coerceIn(-MAX_YAW_OFFSET, MAX_YAW_OFFSET)
        player.yRot = clampedYaw
        player.yHeadRot = clampedYaw
    }

    private const val MAX_YAW_OFFSET = 90f
}
