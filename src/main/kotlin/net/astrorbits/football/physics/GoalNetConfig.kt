package net.astrorbits.football.physics

/**
 * 球网（实体化）的物理与渲染常量。
 *
 * 这些参数独立于 [net.astrorbits.football.config.server.PhysicsSettings]，
 * 以避免改动已持久化 / 已同步的足球物理配置编解码格式。
 */
object GoalNetConfig {
    /** 节点之间的目标间距（方块）。网格分辨率据此推导。 */
    const val TARGET_NODE_SPACING: Double = 0.5

    /** 单一轴向上的节点数下限 / 上限，避免过密造成同步与模拟开销过大。 */
    const val MIN_NODES_PER_AXIS: Int = 3
    const val MAX_NODES_PER_AXIS: Int = 12

    /** 每 tick 施加的重力（Verlet 位移量，方块/tick^2 量级）。 */
    const val GRAVITY: Double = 0.018

    /** Verlet 速度阻尼，越小越快静止。 */
    const val DAMPING: Double = 0.97

    /** 约束（弹簧）迭代次数。越多越硬，开销越大。 */
    const val CONSTRAINT_ITERATIONS: Int = 6

    /** 松弛度默认值与范围。松弛度越大，弹簧静止长度越长，网越下垂。 */
    const val DEFAULT_SLACK: Double = 0.08
    const val MIN_SLACK: Double = 0.0
    const val MAX_SLACK: Double = 0.45
    const val SLACK_STEP: Double = 0.05

    /** 球网被扰动后保持“活跃模拟+同步”的额外 tick 数；静止后降频以省性能。 */
    const val ACTIVE_TICKS_AFTER_DISTURB: Int = 60

    /** 节点静止判定：相邻 tick 位移平方和低于该值视为静止。 */
    const val SETTLE_SPEED_SQR: Double = 1.0e-7

    /** 足球与网交互：法向速度被吸收的比例（1 = 完全吸收）。 */
    const val BALL_NORMAL_ABSORPTION: Double = 0.82

    /** 足球与网交互：切向速度保留比例（实现“贴网下滑”）。 */
    const val BALL_TANGENT_RETENTION: Double = 0.7

    /** 足球推动网时，冲量影响的节点半径（方块）。 */
    const val BALL_PUSH_RADIUS: Double = 1.25

    /** 足球把网“顶出”的最大附加位移系数，避免穿透过深。 */
    const val BALL_PUSH_STRENGTH: Double = 0.9

    /** 球心到网面距离小于 (球半径 + 该值) 时判定接触。 */
    const val CONTACT_MARGIN: Double = 0.12

    /** 渲染：绳线在世界空间的半宽（方块）。固定世界宽度 => 视觉粗细随距离变化。 */
    const val LINE_HALF_WIDTH: Double = 0.013

    /** 渲染：远处线宽的最小屏幕保护系数，避免过远完全消失（按距离放大半宽）。 */
    const val LINE_WIDTH_DISTANCE_GAIN: Double = 0.0016

    /** 绳线颜色（ARGB）。 */
    const val LINE_COLOR_ARGB: Int = 0xFFEDEDED.toInt()
}
