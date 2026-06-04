package net.astrorbits.football.match

import net.astrorbits.football.network.FootballNetworking
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * 进球或出界后，延迟将足球复位至目标位置（默认开球点）。
 * 延迟秒数见 [MatchConfig.postGoalBallResetDelaySeconds]。
 */
object PostGoalBallResetScheduler {
    private data class Pending(
        val dimension: ResourceKey<Level>,
        var ticksRemaining: Int,
        val resetPos: Vec3?,
        val afterReset: PendingAfterReset?,
    )

    private val pending = ArrayList<Pending>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    fun schedule(level: ServerLevel, resetPos: Vec3? = null, afterReset: PendingAfterReset? = null) {
        val delaySeconds = MatchConfigHolder.current.postGoalBallResetDelaySeconds.coerceAtLeast(0)
        val delayTicks = delaySeconds * 20
        cancel(level.dimension())
        if (delayTicks <= 0) {
            MatchState.resetFootball(level, resetPos)
            afterReset?.let { applyAfterReset(level.server, it) }
            return
        }
        pending.add(Pending(level.dimension(), delayTicks, resetPos, afterReset))
    }

    private fun applyAfterReset(server: MinecraftServer?, action: PendingAfterReset) {
        val srv = server ?: return
        MatchState.kickoffTeam = action.kickoffTeam
        when (action) {
            is PendingAfterReset.PostGoal -> {
                MatchState.beginKickoffPhase(MatchKickoffTiming.POST_GOAL_LOCK_MS, KickoffWhistleContext.POST_GOAL)
                FootballNetworking.broadcastRestartKickoff(srv, action.kickoffTeam, goalLineOut = false)
            }
            is PendingAfterReset.GoalLineOut -> {
                MatchState.beginKickoffPhase(MatchKickoffTiming.GOAL_LINE_OUT_LOCK_MS, KickoffWhistleContext.GOAL_LINE_OUT)
                if (action.throwInDirectGoalRestrict) {
                    MatchState.beginThrowInDirectGoalRestriction()
                }
                FootballNetworking.broadcastRestartKickoff(srv, action.kickoffTeam, goalLineOut = true)
            }
        }
    }

    fun cancel(dimension: ResourceKey<Level>) {
        pending.removeIf { it.dimension == dimension }
    }

    private fun tick(server: MinecraftServer) {
        if (pending.isEmpty()) return
        val iter = pending.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            entry.ticksRemaining--
            if (entry.ticksRemaining > 0) continue
            iter.remove()
            val level = server.getLevel(entry.dimension) ?: continue
            MatchState.resetFootball(level, entry.resetPos)
            entry.afterReset?.let { applyAfterReset(server, it) }
        }
    }
}
