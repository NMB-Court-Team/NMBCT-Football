package net.astrorbits.football.client

import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.network.FootballKickCurveC2SPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import kotlin.math.abs

object KickCurveClient {
    private var active = false
    private var kickYaw = 0f
    private var windowEndMs = 0L
    /** 窗口内相对射门朝向的最大偏航（带符号）。 */
    private var peakYawDelta = 0f

    fun begin(kickYaw: Float, chargeRatio: Float) {
        if (chargeRatio < FootballInputConfig.CURVE_MIN_CHARGE_RATIO) {
            reset()
            return
        }
        this.kickYaw = kickYaw
        windowEndMs = System.currentTimeMillis() + FootballInputConfig.CURVE_WINDOW_MS
        peakYawDelta = 0f
        active = true
    }

    fun tick(player: LocalPlayer) {
        if (!active) {
            return
        }
        val now = System.currentTimeMillis()
        if (now < windowEndMs) {
            val delta = Mth.wrapDegrees(player.yHeadRot - kickYaw)
            if (abs(delta) > abs(peakYawDelta)) {
                peakYawDelta = delta
            }
            return
        }

        active = false
        val peak = peakYawDelta.coerceIn(
            -FootballInputConfig.CURVE_MAX_YAW_DEG.toFloat(),
            FootballInputConfig.CURVE_MAX_YAW_DEG.toFloat(),
        )
        if (abs(peak) < FootballInputConfig.CURVE_MIN_YAW_DEG) {
            return
        }
        if (!ClientPlayNetworking.canSend(FootballKickCurveC2SPayload.TYPE)) {
            return
        }
        ClientPlayNetworking.send(FootballKickCurveC2SPayload(peak))
    }

    fun reset() {
        active = false
        windowEndMs = 0L
        peakYawDelta = 0f
    }

    fun isActive(): Boolean = active
}
