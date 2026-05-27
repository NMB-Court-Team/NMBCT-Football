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
    const val CHIP_ANGLE_DEG = 50

    /** 挑球仰角随玩家视线微调的额外上限（度），叠加在 [CHIP_ANGLE_DEG] 之上。 */
    const val CHIP_ANGLE_EXTRA_MAX = 20

    /**
     * 挑球触球点相对球心的竖直偏移（格）。
     * 负值表示触球点偏下，便于产生向上挑起的效果。
     */
    const val CHIP_HEIGHT_OFFSET = -0.15

    /** 运球目标点：玩家脚前方水平距离（格）。 */
    const val DRIBBLE_TARGET_DISTANCE = 0.9

    /** 运球 session 最大控制距离（格）；超出则结束 session。 */
    const val DRIBBLE_MAX_CONTROL_RANGE = 2.5

    /** 未收到 DRIBBLE_HOLD 心跳超过该 tick 数则自动结束 session。 */
    const val DRIBBLE_SESSION_TIMEOUT_TICKS = 6

    /** 客户端 DRIBBLE_HOLD 发包间隔（tick）。 */
    const val DRIBBLE_HOLD_PACKET_INTERVAL = 2

    /** 位置误差 → 速度修正（PD 控制器 P 项）。 */
    const val DRIBBLE_POSITION_GAIN = 0.35

    /** 速度误差 → 速度修正（PD 控制器 D 项）。 */
    const val DRIBBLE_VELOCITY_GAIN = 0.55

    /** 每 tick 最大速度修正量（blocks/tick）。 */
    const val DRIBBLE_MAX_CORRECTION = 0.12

    /** 球速跟随玩家水平速度的比例（0~1）。 */
    const val DRIBBLE_SPEED_MATCH = 0.85

    /** 侧向偏移拉回力度（0~1）；越小侧向跟得越紧。 */
    const val DRIBBLE_LATERAL_GAIN = 0.5

    /** session 开始时可选轻触球力度；0 表示关闭。 */
    const val DRIBBLE_TOUCH_FORCE = 0.08

    /** 球在空中时位置拉回增益缩放（减弱吸地感）。 */
    const val DRIBBLE_AIR_POSITION_SCALE = 0.25

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
