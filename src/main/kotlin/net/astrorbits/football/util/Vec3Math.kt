package net.astrorbits.football.util

import net.minecraft.world.phys.Vec3

object Vec3Math {
    fun horizontal(vector: Vec3): Vec3 = Vec3(vector.x, 0.0, vector.z)

    fun normalizeSafe(vector: Vec3, fallback: Vec3 = Vec3.ZERO): Vec3 {
        val lengthSqr = vector.lengthSqr()
        return if (lengthSqr < 1.0e-8) fallback else vector.scale(1.0 / Math.sqrt(lengthSqr))
    }

    fun cross(left: Vec3, right: Vec3): Vec3 = left.cross(right)

    fun projectOnPlane(vector: Vec3, planeNormal: Vec3): Vec3 {
        val normal = normalizeSafe(planeNormal, Vec3(0.0, 1.0, 0.0))
        return vector.subtract(normal.scale(vector.dot(normal)))
    }

    fun scaleComponents(vector: Vec3, scale: Double): Vec3 = vector.scale(scale)
}
