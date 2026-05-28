package net.astrorbits.football

/**
 * 足球粒子效果的全局数值配置。
 *
 * 修改本文件中的常量即可调整游戏内粒子密度与触发阈值，无需改动 [FootballParticles] 的调用点。
 */
object FootballParticleConfig {
    /** 落地反弹粒子：低于该竖直接近速度（blocks/tick）不播放。 */
    const val BOUNCE_PARTICLE_MIN_GROUND_VY = 0.12

    /** 撞墙反弹粒子：低于该水平接近速度（blocks/tick）不播放。 */
    const val BOUNCE_PARTICLE_MIN_WALL_SPEED = 0.08

    /** 落地/撞墙粒子数量随冲击速度缩放的参考速度（blocks/tick）。 */
    const val BOUNCE_PARTICLE_REFERENCE_SPEED = 0.45

    /** 踢球粒子基础数量；实际数量会随力度增加。 */
    const val KICK_COUNT_BASE = 8

    /** 踢球粒子随满力度额外增加的最大数量。 */
    const val KICK_COUNT_FORCE_EXTRA = 12

    /** 带球触球粒子数量。 */
    const val DRIBBLE_COUNT = 3

    /** 停球粒子数量。 */
    const val TRAP_COUNT = 6

    /** 放置足球粒子数量。 */
    const val PLACE_COUNT = 8

    const val GK_CATCH_COUNT = 6
    const val GK_DIVE_COUNT = 10
    const val GK_PUNCH_COUNT = 8
    const val GK_THROW_COUNT = 6

    /** 粒子散布半径（格），用于水平方向。 */
    const val SPREAD_HORIZONTAL = 0.22

    /** 粒子散布半径（格），用于竖直方向。 */
    const val SPREAD_VERTICAL = 0.12

    /** 踢球/击球类粒子的初速度缩放。 */
    const val KICK_PARTICLE_SPEED = 0.06

    /** 反弹类粒子的初速度缩放。 */
    const val BOUNCE_PARTICLE_SPEED = 0.04

    /** 守门员鱼跃拖尾粒子初速度。 */
    const val GK_DIVE_PARTICLE_SPEED = 0.03
}
