package net.astrorbits.football.util

import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

object Vec3Math {
    data class PlanarDecomposition(
        val alongNormal: Vec3,
        val tangent: Vec3,
        /** 有符号标量 `velocity · unitNormal`。 */
        val normalComponent: Double,
    ) {
        val tangentSpeed: Double get() = tangent.length()
    }

    /** 将 [velocity] 分解为沿 [unitNormal] 的分量与切向余量（墙反弹/推球共用）。 */
    fun decomposePlanar(velocity: Vec3, unitNormal: Vec3): PlanarDecomposition {
        val component = velocity.dot(unitNormal)
        val along = unitNormal.scale(component)
        return PlanarDecomposition(along, velocity.subtract(along), component)
    }

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
