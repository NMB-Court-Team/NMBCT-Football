package net.astrorbits.football.client.match

import net.astrorbits.football.match.MatchState

/** 本地被罚下：底部回归倒计时（仅被罚下球员）。 */
object SendOffLocalClient {
    var expireAtTimerTicks: Int = 0; private set

    val isActive: Boolean get() = expireAtTimerTicks > 0

    val remainingTicks: Int
        get() = if (expireAtTimerTicks <= 0) 0 else (expireAtTimerTicks - MatchState.timerTicks).coerceAtLeast(0)

    fun begin(expireAt: Int) {
        expireAtTimerTicks = expireAt
    }

    fun clear() {
        expireAtTimerTicks = 0
    }

    fun formatRemaining(): String = MatchState.formatElapsed(remainingTicks)
}
