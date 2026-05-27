package net.astrorbits.football.physics

/** 本 tick 碰撞解析产生的反弹事件，用于触发音效等反馈。 */
data class CollisionBounceResult(
    /** 落地前竖直接近速度（向下为正，blocks/tick）。 */
    val groundImpactSpeed: Double = 0.0,
    /** 撞墙前沿法向接近速度（blocks/tick）。 */
    val wallImpactSpeed: Double = 0.0,
) {
    val hasGroundBounce: Boolean get() = groundImpactSpeed > 0.0
    val hasWallBounce: Boolean get() = wallImpactSpeed > 0.0
}
