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

    /** 墙体/水平碰撞恢复系数（0~1）。撞墙后水平速度分量乘以该值，通常略低于地面弹性。 */
    const val WALL_RESTITUTION = 0.55

    /** 地面切向摩擦系数（每 tick 乘数，0~1）。接地时水平速度每 tick 乘以该值，越小减速越快。 */
    const val GROUND_FRICTION = 0.92

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
     * 客户端预测校正阈值（blocks/tick）。
     * 当本地线速度与同步速度之差超过该值时，强制对齐服务端状态并调用 lerpMotion。
     */
    const val CLIENT_CORRECTION_THRESHOLD = 0.5
}
