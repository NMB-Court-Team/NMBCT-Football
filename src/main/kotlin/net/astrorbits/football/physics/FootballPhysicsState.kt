package net.astrorbits.football.physics

import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

data class FootballPhysicsState(
    var linearVelocity: Vec3 = Vec3.ZERO,
    var angularVelocity: Vec3 = Vec3.ZERO,
    var onGround: Boolean = false,
    var orientation: Quaternionf = Quaternionf()
)
