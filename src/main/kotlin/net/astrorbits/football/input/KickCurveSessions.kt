package net.astrorbits.football.input

import net.astrorbits.football.Football
import net.astrorbits.football.util.Vec3Math
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

object KickCurveSessions {
    const val COMMIT_GRACE_TICKS = 8L

    private data class Pending(
        val footballId: Int,
        val kickHorizontal: Vec3,
        val chargeRatio: Float,
        val commitAfterTick: Long,
        val discardAfterTick: Long,
    )

    private val pending = ConcurrentHashMap<UUID, Pending>()

    fun registerServerTick() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    private fun tick(server: MinecraftServer) {
        if (pending.isEmpty()) {
            return
        }
        val now = server.overworld().gameTime
        pending.entries.removeIf { (_, session) -> now > session.discardAfterTick }
    }

    fun begin(
        player: ServerPlayer,
        football: Football,
        kickHorizontal: Vec3,
        chargeRatio: Float,
        now: Long,
    ) {
        if (chargeRatio < FootballInputConfig.CURVE_MIN_CHARGE_RATIO) {
            pending.remove(player.uuid)
            return
        }
        val horiz = Vec3Math.normalizeSafe(Vec3Math.horizontal(kickHorizontal))
        if (horiz.lengthSqr() < 1.0e-8) {
            return
        }
        val windowTicks = (FootballInputConfig.CURVE_WINDOW_MS * 20L + 999L) / 1000L
        val window = windowTicks.coerceAtLeast(1L)
        pending[player.uuid] = Pending(
            footballId = football.id,
            kickHorizontal = horiz,
            chargeRatio = chargeRatio.coerceIn(0f, 1f),
            commitAfterTick = now + window,
            discardAfterTick = now + window + COMMIT_GRACE_TICKS,
        )
    }

    fun tryApplyCurve(player: ServerPlayer, curveYawDeltaDeg: Float, now: Long): Boolean {
        val session = pending[player.uuid] ?: return false
        if (now < session.commitAfterTick) {
            return false
        }

        val football = player.level().getEntity(session.footballId) as? Football ?: run {
            pending.remove(player.uuid)
            return false
        }
        if (!football.isAlive) {
            pending.remove(player.uuid)
            return false
        }
        // 须在 pending 仍存在时检查：isPlayerBallMovementForbidden 靠 isFollowUpActive 豁免点球 RESOLVING 开球锁。
        if (football.isPlayerBallMovementForbidden(player)) {
            pending.remove(player.uuid)
            return false
        }

        val yawDelta = curveYawDeltaDeg.toDouble()
            .coerceIn(-FootballInputConfig.CURVE_MAX_YAW_DEG, FootballInputConfig.CURVE_MAX_YAW_DEG)
        if (abs(yawDelta) < FootballInputConfig.CURVE_MIN_YAW_DEG) {
            pending.remove(player.uuid)
            return false
        }

        val ratio = abs(yawDelta) / FootballInputConfig.CURVE_MAX_YAW_DEG
        val targetLateralSpeed = FootballInputConfig.CURVE_MAX_LATERAL_SPEED *
            ratio * session.chargeRatio.toDouble()
        if (targetLateralSpeed < 1.0e-6) {
            pending.remove(player.uuid)
            return false
        }

        val applied = football.beginCurveRamp(session.kickHorizontal, yawDelta, targetLateralSpeed, player)
        pending.remove(player.uuid)
        return applied
    }

    fun clear(playerId: UUID) {
        pending.remove(playerId)
    }

    /** 蓄力射门松开后至弧线提交/丢弃前，主罚可能仍在转头调弧线。 */
    fun isFollowUpActive(playerUuid: UUID, footballId: Int, now: Long): Boolean {
        val session = pending[playerUuid] ?: return false
        if (session.footballId != footballId) return false
        return now <= session.discardAfterTick
    }
}
