package net.astrorbits.football.input

import net.astrorbits.football.util.FootballKickUtil
import net.astrorbits.football.util.GoalkeeperUtil
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class DiveSession(
    val playerId: UUID,
    val forwardDirection: Vec3,
    val chargeRatio: Float,
    val pitchScalars: GoalkeeperUtil.DivePitchScalars,
    val startTick: Long,
)

object GoalkeeperDiveSessions {
    private val sessions = ConcurrentHashMap<UUID, DiveSession>()

    fun registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    fun begin(player: ServerPlayer, direction: Vec3, chargeRatio: Float, lookPitch: Float, now: Long) {
        val clampedCharge = chargeRatio.coerceIn(0f, 1f)
        val pitchScalars = GoalkeeperUtil.resolveDivePitchScalars(lookPitch)
        applyDiveLaunch(player, direction, clampedCharge, pitchScalars)

        sessions[player.uuid] = DiveSession(
            playerId = player.uuid,
            forwardDirection = direction,
            chargeRatio = clampedCharge,
            pitchScalars = pitchScalars,
            startTick = now,
        )
    }

    fun isDiving(player: ServerPlayer): Boolean = sessions.containsKey(player.uuid)

    fun tick(server: MinecraftServer) {
        if (sessions.isEmpty()) {
            return
        }

        val now = server.overworld().gameTime
        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            val (playerId, session) = iterator.next()
            val player = server.playerList.getPlayer(playerId)
            if (player == null) {
                iterator.remove()
                continue
            }

            val elapsed = now - session.startTick
            if (elapsed >= GoalkeeperInputConfig.GK_DIVE_DURATION_TICKS) {
                iterator.remove()
                continue
            }

            applyDiveMovement(player, session)
            tryResolveSave(player, session)
        }
    }

    /** 起跳瞬间：水平 + 竖直冲量（与滑铲类似，需 hurtMarked 以便客户端同步速度）。 */
    private fun applyDiveLaunch(
        player: ServerPlayer,
        direction: Vec3,
        chargeRatio: Float,
        pitchScalars: GoalkeeperUtil.DivePitchScalars,
    ) {
        val charge = chargeRatio.toDouble()
        val base = GoalkeeperInputConfig.GK_DIVE_SPEED
        val impulse = GoalkeeperInputConfig.GK_DIVE_IMPULSE
        val horizontalSpeed = lerp(
            base * impulse.launchForwardMinScale,
            base * impulse.launchForwardMaxScale,
            charge,
        ) * pitchScalars.forwardScale
        val upSpeed = lerp(impulse.launchUpMin, impulse.launchUpMax, charge) * pitchScalars.heightScale
        val horizontal = direction.scale(horizontalSpeed)
        val vertical = if (pitchScalars.groundedDive) {
            pitchScalars.groundVerticalSpeed
        } else {
            upSpeed
        }
        player.setDeltaMovement(horizontal.x, vertical, horizontal.z)
        player.hurtMarked = true
    }

    /** 鱼跃持续期间每 tick 写入水平速度，避免仅一帧冲量被原版移动链路吞掉。 */
    private fun applyDiveMovement(player: ServerPlayer, session: DiveSession) {
        val charge = session.chargeRatio.toDouble()
        val base = GoalkeeperInputConfig.GK_DIVE_SPEED
        val impulse = GoalkeeperInputConfig.GK_DIVE_IMPULSE
        val pitchScalars = session.pitchScalars
        val horizontalSpeed = lerp(
            base * impulse.sustainForwardMinScale,
            base * impulse.sustainForwardMaxScale,
            charge,
        ) * pitchScalars.forwardScale
        val dir = session.forwardDirection
        val motion = player.deltaMovement
        val vertical = if (pitchScalars.groundedDive) {
            motion.y.coerceAtMost(pitchScalars.groundVerticalSpeed)
        } else {
            motion.y
        }
        player.setDeltaMovement(dir.x * horizontalSpeed, vertical, dir.z * horizontalSpeed)
        player.hurtMarked = true
    }

    private fun tryResolveSave(player: ServerPlayer, session: DiveSession) {
        val range = GoalkeeperUtil.diveRange(player)
        val football = FootballKickUtil.findNearestFootball(player, range) ?: return
        if (football.isHeld()) {
            return
        }

        if (!GoalkeeperUtil.canDiveCatchBall(player, football, player.yRot, player.xRot, range)) {
            return
        }

        val centered = GoalkeeperUtil.isDiveCatchCentered(player, football, player.yRot, player.xRot)
        val resolved = if (centered) {
            GoalkeeperActions.tryResolveDiveCatch(player, football, session.forwardDirection) ||
                GoalkeeperActions.tryResolveDiveDeflect(player, football)
        } else {
            GoalkeeperActions.tryResolveDiveDeflect(player, football)
        }
        if (resolved) {
            sessions.remove(player.uuid)
        }
    }

    private fun lerp(min: Double, max: Double, ratio: Double): Double {
        val t = ratio.coerceIn(0.0, 1.0)
        return min + (max - min) * t
    }
}
