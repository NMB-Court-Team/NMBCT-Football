package net.astrorbits.football.config

/** 客户端上报 flags 中的协议位；非可调参数。 */
object FootballInputFlags {
    const val SPRINT = 1
    const val DIVE_USE_LOOK = 2
    /** 观察四周期间带球：球位以进入观察时的 yaw 为基准。 */
    const val LOOK_AROUND = 4
}
