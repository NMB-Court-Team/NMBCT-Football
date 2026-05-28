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

    /** 高速裹挟粒子：触发最小速度（blocks/tick）。 */
    const val HIGH_SPEED_DRAG_MIN_SPEED = 0.42

    /** 高速裹挟粒子：数量基准。 */
    const val HIGH_SPEED_DRAG_COUNT_BASE = 4

    /** 高速裹挟粒子：随速度额外增加的最大数量。 */
    const val HIGH_SPEED_DRAG_COUNT_EXTRA = 12

    /** 高速裹挟粒子：数量拉满参考速度（blocks/tick）。 */
    const val HIGH_SPEED_DRAG_REFERENCE_SPEED = 0.85

    /** 高速裹挟 trail 粒子：前进法平面圆环半径（格）。 */
    const val HIGH_SPEED_DRAG_RING_RADIUS = 0.34

    /** 高速裹挟 trail 粒子：终点沿速度方向前推距离（格）。 */
    const val HIGH_SPEED_DRAG_TRAIL_FORWARD_DISTANCE = 0.50

    /** 高速裹挟 trail 粒子：整体竖直偏移（格）。 */
    const val HIGH_SPEED_DRAG_VERTICAL_OFFSET = 0.25

    /** 高速裹挟 trail 粒子：持续时长（tick）。 */
    const val HIGH_SPEED_DRAG_TRAIL_DURATION_TICKS = 10

    /** 高速裹挟 trail 粒子：低力度颜色（蓝）。 */
    const val HIGH_SPEED_DRAG_TRAIL_COLOR_LOW_RGB = 0x6FB8FF

    /** 高速裹挟 trail 粒子：高力度颜色（红）。 */
    const val HIGH_SPEED_DRAG_TRAIL_COLOR_HIGH_RGB = 0xFF4E4E

    /** 高速裹挟 trail 粒子：开始明显偏红的力度阈值（0~1）。 */
    const val HIGH_SPEED_DRAG_COLOR_RED_START = 0.60f

    /** 踢击提示横扫：脚前方距离（格）。 */
    const val KICK_SWEEP_FOOT_FORWARD = 0.15

    /** 踢击提示横扫：离地高度（格）。 */
    const val KICK_SWEEP_FOOT_HEIGHT = 0.10

    /** 踢球云环：基础粒子数（单环）。 */
    const val KICK_CLOUD_RING_BASE_COUNT = 8

    /** 踢球云环：力度额外粒子数（单环）。 */
    const val KICK_CLOUD_RING_FORCE_EXTRA = 8

    /** 踢球云环：内环半径（格）。 */
    const val KICK_CLOUD_RING_INNER_RADIUS = 0.68

    /** 踢球云环：外环半径（格）。 */
    const val KICK_CLOUD_RING_OUTER_RADIUS = 1.00

    /** 踢球云环：径向扩散速度。 */
    const val KICK_CLOUD_RING_RADIAL_SPEED = 0.035
}
