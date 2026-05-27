package net.astrorbits.football.physics

/**
 * 足球物理模拟的全局常量配置。
 *
 * 速度单位为 blocks/tick，角速度单位为 rad/tick，除非另有说明。
 */
object FootballPhysicsConfig {
    /** 足球碰撞半径（米），与实体 [net.astrorbits.football.Football] 的半宽一致。 */
    const val RADIUS = 0.25

    /** 足球质量（任意单位），用于计算线速度冲量：Δv = 冲量 / MASS。 */
    const val MASS = 0.45

    /** 转动惯量（实心球近似：2/5·m·r²），用于计算角速度冲量：Δω = 扭矩 / INERTIA。 */
    const val INERTIA = 2.0 / 5.0 * MASS * RADIUS * RADIUS

    /** 地面法向恢复系数（0~1）。落地时竖直速度反向并乘以该值，越大弹跳越高。 */
    const val RESTITUTION = 0.68

    /** 墙体/水平碰撞恢复系数（0~1）。撞墙后沿法向速度反向并乘以该值。 */
    const val WALL_RESTITUTION = 0.68

    /**
     * 撞墙后保留的自转比例（0~1）。
     * 真实碰撞中大量旋转能会转化为形变/热；保留过高会导致「弹开却仍朝墙滚」的反直觉感。
     */
    const val WALL_SPIN_RETENTION = 0.3

    /** 撞墙后跳过滚动耦合的 tick 数，让线速度反弹方向优先于自转。 */
    const val WALL_BOUNCE_COOLDOWN_TICKS = 5

    /** 撞墙判定：被挡位移低于意图位移该比例时视为撞墙（MC 的 actual 位移很少精确为 0）。 */
    const val WALL_BLOCK_RATIO = 0.35

    /** 撞墙时绕竖直轴自转的额外衰减（0~1）。 */
    const val WALL_YAW_SPIN_DAMP = 0.35

    /** 地面切向摩擦系数（每 tick 乘数，0~1）。接地时水平速度每 tick 乘以该值，越小减速越快。 */
    const val GROUND_FRICTION = 0.92

    /** 地面自转摩擦系数（每 tick 乘数，0~1）。接地时角速度每 tick 乘以该值，防止撞墙后空转加速。 */
    const val GROUND_SPIN_FRICTION = 0.92

    /**
     * 贴墙/几乎静止时的自转衰减（每 tick 乘数）。
     * 水平被挡且线速度很小时额外施加，避免原地越转越快。
     */
    const val STUCK_SPIN_DRAG = 0.65

    /** 低于该水平速度（blocks/tick）² 时视为静止，清零水平线速度与水平自转。 */
    const val STOP_SPEED_SQR = 1.0e-6

    /**
     * 接地时低于该值的向下竖直速度（blocks/tick）视为贴地 settling，直接清零而不反弹。
     * 避免静止球因每 tick 重力 + 弹性反弹产生 Y 轴微振荡。
     */
    const val GROUND_SETTLE_VY = 0.08

    /**
     * 客户端渲染：低于该速度² 时改用原版 `getPosition` 插值，不用速度外推（静止时避免上下抖动）。
     */
    const val RENDER_STATIONARY_SPEED_SQR = 1.0e-4

    /**
     * 接地时绕竖直轴（偏航）自转的额外衰减（每 tick 乘数）。
     * 地面滚动球不应长期绕 Y 轴空转；与 [GROUND_SPIN_FRICTION] 叠乘。
     */
    const val GROUND_YAW_SPIN_FRICTION = 0.65

    /** 空气阻力系数（每 tick 乘数，0~1）。空中时每 tick 线速度整体乘以该值。 */
    const val AIR_DRAG = 0.99

    /** 自转空气阻力系数（每 tick 乘数，0~1）。空中时每 tick 角速度乘以该值。 */
    const val SPIN_DRAG = 0.995

    /** 重力加速度（blocks/tick²）。每 tick 从线速度的 Y 分量中减去该值（实体已关闭原版重力）。 */
    const val GRAVITY = 0.04

    /**
     * 纯滚动耦合强度（0~1）。接地时将线速度与角速度向无滑滚动关系拉近；
     * 越大越快达到 v ≈ ω × r，越小滑动感越强。
     */
    const val ROLL_COUPLING = 0.15

    /** 蜘蛛网内水平方向速度衰减（每 tick 乘数）。与原版蜘蛛网水平系数 0.25 一致。 */
    const val COBWEB_HORIZONTAL_DRAG = 0.25

    /** 蜘蛛网内竖直方向速度衰减（每 tick 乘数）。与原版蜘蛛网竖直系数 0.05 一致。 */
    const val COBWEB_VERTICAL_DRAG = 0.05

    /** 蜘蛛网内自转角速度衰减（每 tick 乘数）。 */
    const val COBWEB_SPIN_DRAG = 0.5

    /** 数值计算用的极小量阈值，用于判断速度/方向是否近似为零。 */
    const val EPSILON = 1.0e-4

    /**
     * 踢球冲量缩放系数。
     * 命令参数 `force` 与视线方向相乘后再乘本系数得到冲量；`force=1` 时约 0.4 blocks/tick 线速增量。
     */
    const val KICK_FORCE_SCALE = 0.18

    /**
     * 对运动中足球再踢时，保留的旧速度「侧向分量」比例（0~1）。
     * 越小越容易随新踢球方向转向；1 为纯矢量叠加。
     */
    const val KICK_MOVING_LATERAL_DAMP = 0.15

    /** 线速度/角速度变化超过该值时重置渲染朝向（blocks/tick 或 rad/tick）。 */
    const val ORIENTATION_RESET_VELOCITY_DELTA = 0.06

    /** 角速度变化超过该值时重置渲染朝向（rad/tick）。 */
    const val ORIENTATION_RESET_OMEGA_DELTA = 0.06

    /**
     * 客户端预测校正阈值（blocks/tick）。
     * 当本地线速度与同步速度之差超过该值时，调用 lerpMotion 平滑位置。
     */
    const val CLIENT_CORRECTION_THRESHOLD = 0.25
}
