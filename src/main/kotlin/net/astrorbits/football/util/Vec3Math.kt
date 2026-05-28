package net.astrorbits.football.util

import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

object Vec3Math {
    fun horizontal(vector: Vec3): Vec3 = Vec3(vector.x, 0.0, vector.z)

    fun normalizeSafe(vector: Vec3, fallback: Vec3 = Vec3.ZERO): Vec3 {
        val lengthSqr = vector.lengthSqr()
        return if (lengthSqr < 1.0e-8) fallback else vector.scale(1.0 / sqrt(lengthSqr))
    }

    /**
     * 无滑滚动的角速度：水平速度 v 对应自转轴为水平方向（垂直于 v）。
     * 沿 +Z 前进时 ω 沿 +X，符合右手法则。
     */
    fun rollingAngularVelocity(horizontalVelocity: Vec3, radius: Double): Vec3 = Vec3(
        horizontalVelocity.z / radius,
        0.0,
        -horizontalVelocity.x / radius
    )

    fun cross(left: Vec3, right: Vec3): Vec3 = left.cross(right)

    fun projectOnPlane(vector: Vec3, planeNormal: Vec3): Vec3 {
        val normal = normalizeSafe(planeNormal, Vec3(0.0, 1.0, 0.0))
        return vector.subtract(normal.scale(vector.dot(normal)))
    }

    fun scaleComponents(vector: Vec3, scale: Double): Vec3 = vector.scale(scale)
}
