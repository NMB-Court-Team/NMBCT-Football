package net.astrorbits.football.input

/**
 * 玩家直接操作足球的输入与踢球力度配置。
 *
 * 力度参数与 [net.astrorbits.football.FootballCommand] 一致：作为 `kick(direction)` 中
 * `direction` 向量的模长（即命令里的 `force`），再经 [net.astrorbits.football.physics.FootballPhysicsConfig.KICK_FORCE_SCALE] 换算为物理冲量。
 */
object FootballInputConfig {
    /** 玩家按键操作时，可选取并踢动的最近足球水平距离（格）。 */
    const val PLAYER_KICK_RANGE = 2.5

    /** `/football kick` 命令选取足球的水平距离（格），略大于玩家操作范围。 */
    const val COMMAND_KICK_RANGE = 3.0

    /** 传球（踢球键短按）时的力度，约为水平中等速度。 */
    const val PASS_FORCE = 1.5

    /** 射门蓄力最低力度（刚达到蓄力阈值松开时）。 */
    const val SHOOT_FORCE_MIN = 2.0

    /** 射门蓄力满格时的最大力度。 */
    const val SHOOT_FORCE_MAX = 4.0

    /** 冲刺且蓄力足够时，射门力度的额外乘数（1.15 = +15%）。 */
    const val SHOOT_SPRINT_BONUS = 1.15

    /**
     * 启用 [SHOOT_SPRINT_BONUS] 所需的最低蓄力比例（0~1）。
     * 低于该值时冲刺不影响射门力度。
     */
    const val SHOOT_MIN_CHARGE_FOR_SPRINT = 0.6f

    /** 挑球按键的力度。 */
    const val CHIP_FORCE = 1.4

    /** 挑球时相对水平面的基础仰角（度）；正值为向上。 */
    const val CHIP_ANGLE_DEG = 42.0

    /** 挑球仰角随玩家视线微调的额外上限（度），叠加在 [CHIP_ANGLE_DEG] 之上。 */
    const val CHIP_ANGLE_EXTRA_MAX = 13.0

    /**
     * 挑球触球点相对球心的竖直偏移（格）。
     * 负值表示触球点偏下，便于产生向上挑起的效果。
     */
    const val CHIP_HEIGHT_OFFSET = -0.15

    /** 带球时每次轻推的力度。 */
    const val DRIBBLE_FORCE = 0.4

    /** 按住带球键时，两次轻推之间的间隔（tick）。 */
    const val DRIBBLE_INTERVAL_TICKS = 4

    /** 踢球键按下时长低于该值（毫秒）视为短按，触发传球。 */
    const val TAP_MAX_MS = 250L

    /** 踢球键按住达到该时长（毫秒）后进入射门蓄力。 */
    const val CHARGE_MIN_MS = 300L

    /** 射门蓄力上限时长（毫秒）；超过后力度不再增加。 */
    const val CHARGE_MAX_MS = 1200L

    /** 同一玩家两次足球操作之间的最短间隔（tick），防止连按刷屏。 */
    const val ACTION_COOLDOWN_TICKS = 3

    /** 射门蓄力最低时的仰角（度），略带上挑。 */
    const val SHOOT_ANGLE_MIN_DEG = 5.0

    /** 射门蓄力满格时的仰角（度）。 */
    const val SHOOT_ANGLE_MAX_DEG = 18.0

    /** 客户端上报 flags 中的「冲刺」位；服务端据此判断是否应用 [SHOOT_SPRINT_BONUS]。 */
    const val FLAG_SPRINT = 1
}
