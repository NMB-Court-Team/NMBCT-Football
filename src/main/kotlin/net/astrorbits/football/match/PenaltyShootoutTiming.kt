package net.astrorbits.football.match

/** 点球大战每轮开踢的 Banner 与操作锁定时长（服务端/客户端共用毫秒；锁定须长于 Banner）。 */
object PenaltyShootoutTiming {
    const val KICK_BANNER_MS = 6000L
    const val KICK_INTRO_LOCK_MS = 8000L

    init {
        require(KICK_INTRO_LOCK_MS > KICK_BANNER_MS) {
            "Penalty kick intro lock must exceed banner duration"
        }
    }

    /** 开踢前禁止操作时长（服务端 tick，20 TPS）。 */
    val KICK_INTRO_LOCK_TICKS: Int = (KICK_INTRO_LOCK_MS / 50L).toInt()
}
