package net.astrorbits.football.input

/**
 * 守门员操作距离、扑救窗口与开球力度配置。
 */
object GoalkeeperInputConfig {
    /** 接球水平距离（格）。 */
    const val GK_CATCH_RANGE = 3.5

    /** 潜行准备姿势额外接球距离（格）。 */
    const val GK_CROUCH_RANGE_BONUS = 0.3

    /** 超过该球速（blocks/tick）时无法稳定接球，需击出或鱼跃。 */
    const val GK_CATCH_MAX_SPEED = 1.8

    /** 来球方向与「球→门将」方向的最大夹角（度）；超过则无法背身接球。 */
    const val GK_CATCH_ANGLE_DEG = 120.0

    /** 最大持球时长（tick），超时自动放下。 */
    const val GK_HOLD_MAX_TICKS = 300

    /** 持球时球心相对球员脚部的竖直高度（格），低于视线，避免挡视野。 */
    const val GK_HOLD_HEIGHT = 1.05

    /** 持球时球心相对球员脚部的水平前伸距离（格），沿视线水平方向。 */
    const val GK_HOLD_FORWARD = 0.85

    /** 潜行持球时球心额外下降（格）。 */
    const val GK_HOLD_CROUCH_HEIGHT_OFFSET = 0.15

    /** 第一人称持球时额外前伸（格）。 */
    const val GK_HOLD_FIRST_PERSON_EXTRA_FORWARD = 0.15

    /** 第一人称持球时额外下降（格）。 */
    const val GK_HOLD_FIRST_PERSON_EXTRA_DOWN = 0.12

    /** 放下球时落点相对脚前的水平距离（格）。 */
    const val GK_DROP_DISTANCE = 0.5

    /** 鱼跃窗口时长（tick）。 */
    const val GK_DIVE_DURATION_TICKS = 8

    /** 鱼跃冷却（tick）。 */
    const val GK_DIVE_COOLDOWN_TICKS = 24

    /** 鱼跃扑救有效距离（格）。 */
    const val GK_DIVE_RANGE = 4.0

    /** 鱼跃扇形半角（度）。 */
    const val GK_DIVE_HALF_ANGLE_DEG = 45.0

    /** 鱼跃期间门将沿扑救方向位移（blocks/tick）。 */
    const val GK_DIVE_SPEED = 0.35

    /** 鱼跃接住的最大球速（blocks/tick）。 */
    const val GK_DIVE_CATCH_MAX_SPEED = 2.2

    /** 鱼跃挡出时施加力度 = 球速 × 该系数。 */
    const val GK_DIVE_DEFLECT_FORCE_SCALE = 0.6

    /** 击出有效距离（格）。 */
    const val GK_PUNCH_RANGE = 3.0

    /** 击出力度（kick direction 模长）。 */
    const val GK_PUNCH_FORCE = 1.2

    /** 手抛球力度。 */
    const val GK_THROW_SHORT_FORCE = 1.2

    /** 大脚开球最低力度。 */
    const val GK_THROW_LONG_FORCE_MIN = 1.8

    /** 大脚开球满蓄力力度。 */
    const val GK_THROW_LONG_FORCE_MAX = 3.2

    /** 大脚开球最低仰角（度）。 */
    const val GK_THROW_LONG_ANGLE_MIN_DEG = 5.0

    /** 大脚开球满蓄力仰角（度）。 */
    const val GK_THROW_LONG_ANGLE_MAX_DEG = 12.0

    /** 冲刺时长抛力度的额外乘数。 */
    const val GK_THROW_SPRINT_BONUS = 1.1

    /** 同一门将两次操作的最短间隔（tick）。 */
    const val GK_ACTION_COOLDOWN_TICKS = 3
}
