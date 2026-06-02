package net.astrorbits.football.match

/** 开球锁定来源，决定倒计时结束瞬间是否吹哨。 */
enum class KickoffWhistleContext {
    MATCH_START,
    POST_GOAL,
    GOAL_LINE_OUT,
    HALF,
}
