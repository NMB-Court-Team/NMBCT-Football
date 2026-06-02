package net.astrorbits.football.match

/** 开球锁定时长（须与客户端 [net.astrorbits.football.client.match.MatchStartClient] 一致）。 */
object MatchKickoffTiming {
    const val MATCH_START_LOCK_MS = 23_000L
    const val POST_GOAL_LOCK_MS = 20_000L
    const val GOAL_LINE_OUT_LOCK_MS = 10_000L
    /** 倒计时结束后，每过该时长仍未触球则吹一声拖延哨（先 3 后 5）。 */
    const val LATE_KICKOFF_WARN_MS = 10_000L
}
