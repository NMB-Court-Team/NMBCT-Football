package net.astrorbits.football.client

object GoalkeeperHoldActionPermissionsClient {
    var canCatch: Boolean = true
        private set
    var canDrop: Boolean = true
        private set
    var canThrow: Boolean = true
        private set

    fun apply(canCatch: Boolean, canDrop: Boolean, canThrow: Boolean) {
        this.canCatch = canCatch
        this.canDrop = canDrop
        this.canThrow = canThrow
    }

    fun reset() {
        apply(canCatch = true, canDrop = true, canThrow = true)
    }
}
