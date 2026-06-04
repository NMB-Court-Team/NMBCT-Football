package net.astrorbits.football.match

import net.minecraft.world.phys.Vec3

private const val PENALTY_MARK_DISTANCE = 11.0

fun GoalConfig.resolvedPenaltySpot(): KickPosition {
    penaltySpot?.let { return it }
    return derivedPenaltySpot()
}

fun GoalConfig.derivedPenaltySpot(): KickPosition {
    val facing = Vec3(facingX, facingY, facingZ)
    val len = facing.length()
    if (len < 1e-6) {
        return goalKick
    }
    val nx = facing.x / len
    val ny = facing.y / len
    val nz = facing.z / len
    val refX = x1 + facing.x
    val refY = y1 + facing.y
    val refZ = z1 + facing.z
    return KickPosition(
        x = refX - nx * PENALTY_MARK_DISTANCE,
        y = refY - ny * PENALTY_MARK_DISTANCE,
        z = refZ - nz * PENALTY_MARK_DISTANCE,
    )
}

fun KickPosition.toVec3(): Vec3 = Vec3(x, y, z)

fun GoalConfig.goalCenter(): Vec3 = Vec3(
    (x1 + x2) / 2.0,
    (y1 + y2) / 2.0,
    (z1 + z2) / 2.0,
)

fun GoalConfig.goalLineFacing(): Vec3 {
    val f = Vec3(facingX, facingY, facingZ)
    return if (f.lengthSqr() < 1e-6) f else f.normalize()
}
