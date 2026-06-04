package net.astrorbits.football.client.match

object MatchPauseClient {
    const val DURATION_MS = 4000L

    var startTimeMs: Long = 0L
        private set
    var paused: Boolean = false
        private set

    val isActive: Boolean
        get() = startTimeMs > 0 && (System.currentTimeMillis() - startTimeMs) < DURATION_MS

    val elapsedMs: Long
        get() = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0L

    fun show(paused: Boolean) {
        this.paused = paused
        startTimeMs = System.currentTimeMillis()
    }
}
