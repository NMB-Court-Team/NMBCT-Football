package net.astrorbits.football.input

interface SlideTackleStateAccess {
    fun nmbctFootball_isSlideTackling(): Boolean
    fun nmbctFootball_setSlideTackling(sliding: Boolean)
}
