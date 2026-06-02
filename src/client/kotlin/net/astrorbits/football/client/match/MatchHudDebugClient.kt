package net.astrorbits.football.client.match

import net.astrorbits.football.match.GoalLineOutType
import net.astrorbits.football.match.MatchPhase
import net.astrorbits.football.match.TeamSide

/** 依次播放比赛事件 HUD 预览（供 /match debugHud 使用）。 */
object MatchHudDebugClient {
    private const val STEP_MS = 4500L

    private data class Queued(val atMs: Long, val action: () -> Unit)

    private val queue = ArrayDeque<Queued>()

    fun scheduleAllPreviews() {
        queue.clear()
        val nameA = MatchHudTeams.name(TeamSide.A)
        val nameB = MatchHudTeams.name(TeamSide.B)
        var at = System.currentTimeMillis()

        fun enqueue(action: () -> Unit) {
            queue.addLast(Queued(at, action))
            at += STEP_MS
        }

        enqueue {
            GoalScoredClient.show(TeamSide.A, "调试球员", TeamSide.A, 2, 1, false, nameA, nameB)
        }
        enqueue {
            GoalLineOutClient.show(
                GoalLineOutType.CORNER_KICK,
                TeamSide.B,
                "调试球员",
                TeamSide.A,
            )
        }
        enqueue {
            MatchStartClient.previewHalfKickoffHud(
                phaseKey = MatchPhase.FIRST_HALF.displayNameKey,
                kickoff = TeamSide.A,
                nameA = nameA,
                nameB = nameB,
            )
        }
        enqueue {
            MatchResultClient.show(2, 1, nameA, nameB, draw = false)
        }
    }

    fun tick() {
        val now = System.currentTimeMillis()
        while (queue.isNotEmpty() && queue.first().atMs <= now) {
            queue.removeFirst().action()
        }
    }
}
