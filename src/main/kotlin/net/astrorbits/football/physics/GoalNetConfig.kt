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

    /** 松弛度默认值与范围。松弛度越大，重力增益更高、约束更柔软，网越下垂。 */
    const val DEFAULT_SLACK: Double = 0.08
    const val MIN_SLACK: Double = 0.0
    const val MAX_SLACK: Double = 0.45
    const val SLACK_STEP: Double = 0.05
    /** 松弛度对重力的增益系数。 */
    const val SLACK_GRAVITY_GAIN: Double = 2.8
    /** 松弛度对弹簧刚度的减弱系数。 */
    const val SLACK_STIFFNESS_REDUCTION: Double = 1.5
    /** 最大松弛度时约束刚度下限，避免网面失稳起皱。 */
    const val MIN_STIFFNESS_SCALE_AT_MAX_SLACK: Double = 0.25

    /** 球网被扰动后保持“活跃模拟+同步”的额外 tick 数；静止后降频以省性能。 */
    const val ACTIVE_TICKS_AFTER_DISTURB: Int = 60

    /** 节点静止判定：相邻 tick 位移平方和低于该值视为静止。 */
    const val SETTLE_SPEED_SQR: Double = 1.0e-7

    /** 足球与网交互：法向速度被吸收的比例（1 = 完全吸收）。 */
    const val BALL_NORMAL_ABSORPTION: Double = 0.72

    /** 足球与网交互：常规切向速度保留比例（实现“触网减速”）。 */
    const val BALL_TANGENT_RETENTION: Double = 0.62

    /** 足球与网交互：重压/极限拉伸时的切向速度保留比例。 */
    const val BALL_TANGENT_RETENTION_HARD: Double = 0.38

    /** 触网回弹：基础法向反弹系数。 */
    const val BALL_RESTITUTION_BASE: Double = 0.08

    /** 触网回弹：随“压入/拉伸强度”提升的法向反弹增益。 */
    const val BALL_RESTITUTION_STRETCH_GAIN: Double = 0.52

    /** 触网回弹：法向反弹系数上限，避免蹦网过强。 */
    const val BALL_RESTITUTION_MAX: Double = 0.62

    /** 自旋阻尼：常规触网时保留比例。 */
    const val BALL_SPIN_RETENTION: Double = 0.78

    /** 自旋阻尼：重压/极限拉伸时保留比例（更强抑制一直转）。 */
    const val BALL_SPIN_RETENTION_HARD: Double = 0.32

    /** “高速硬碰”速度阈值（方块/tick），用于估算拉伸强度。 */
    const val HARD_CONTACT_SPEED: Double = 0.72

    /** 足球推动网时，冲量影响的节点半径（方块）。 */
    const val BALL_PUSH_RADIUS: Double = 1.25

    /** 足球把网“顶出”的最大附加位移系数，避免穿透过深。 */
    const val BALL_PUSH_STRENGTH: Double = 0.9

    /** 球心到网面距离小于 (球半径 + 该值) 时判定接触。 */
    const val CONTACT_MARGIN: Double = 0.12

    /** 触网后与网面保留的基础分离距离，减少“黏网”观感。 */
    const val CONTACT_SEPARATION: Double = 0.02

    /** 分离距离随压入深度的放大系数。 */
    const val CONTACT_SEPARATION_FROM_PENETRATION: Double = 0.45

    /** 网接近极限拉伸时，额外法向反推速度增益（防穿透）。 */
    const val STRETCH_PUSHOUT_VELOCITY_GAIN: Double = 0.42

    /** 渲染：绳线在世界空间的半宽（方块）。固定世界宽度 => 视觉粗细随距离变化。 */
    const val LINE_HALF_WIDTH: Double = 0.018

    /** 渲染：远处线宽的最小屏幕保护系数，避免过远完全消失（按距离放大半宽）。 */
    const val LINE_WIDTH_DISTANCE_GAIN: Double = 0.0016

    /** 绳线颜色（ARGB）。 */
    const val LINE_COLOR_ARGB: Int = 0xFFEDEDED.toInt()
}
