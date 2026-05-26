package net.astrorbits.football.util

import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object QuaternionMath {
    fun integrate(orientation: Quaternionf, angularVelocity: Vec3): Quaternionf {
        val wx = angularVelocity.x.toFloat()
        val wy = angularVelocity.y.toFloat()
        val wz = angularVelocity.z.toFloat()
        val omegaLength = sqrt(wx * wx + wy * wy + wz * wz)
        if (omegaLength < 1.0e-6f) {
            return Quaternionf(orientation)
        }

        val halfAngle = omegaLength * 0.5f
        val sinHalf = sin(halfAngle)
        val cosHalf = cos(halfAngle)
        val delta = Quaternionf(
            wx / omegaLength * sinHalf,
            wy / omegaLength * sinHalf,
            wz / omegaLength * sinHalf,
            cosHalf
        )
        return Quaternionf(orientation).mul(delta).normalize()
    }

    fun slerp(from: Quaternionf, to: Quaternionf, alpha: Float): Quaternionf {
        return Quaternionf(from).slerp(to, alpha)
    }
}
