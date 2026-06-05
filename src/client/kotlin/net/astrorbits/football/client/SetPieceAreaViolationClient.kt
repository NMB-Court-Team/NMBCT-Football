package net.astrorbits.football.client

object SetPieceAreaViolationClient {
    var areaNameKey: String = ""; private set
    var secondsRemaining: Int = 0; private set

    fun update(areaNameKey: String, secondsRemaining: Int) {
        this.areaNameKey = areaNameKey
        this.secondsRemaining = secondsRemaining
    }

    fun clear() {
        areaNameKey = ""
        secondsRemaining = 0
    }

    fun isActive(): Boolean = areaNameKey.isNotBlank() && secondsRemaining > 0
}
