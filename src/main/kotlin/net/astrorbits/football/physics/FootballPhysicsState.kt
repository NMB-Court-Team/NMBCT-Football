package net.astrorbits.football.physics

import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

data class FootballPhysicsState(
    var linearVelocity: Vec3 = Vec3.ZERO,
    var angularVelocity: Vec3 = Vec3.ZERO,
    var onGround: Boolean = false,
    var inCobweb: Boolean = false,
    var orientation: Quaternionf = Quaternionf(),
    /** 撞墙后剩余 tick 数；期间跳过滚动耦合，避免旧自转把线速度拉回墙面。 */
    var wallBounceCooldown: Int = 0
)
