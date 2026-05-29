package net.astrorbits.football.util

import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.physics.FootballPhysicsState
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object CobwebUtil {
    /**
     * Returns true when the entity bounding box overlaps any cobweb block.
     */
    fun isIntersectingCobweb(level: Level, boundingBox: AABB): Boolean {
        val min = BlockPos.containing(
            boundingBox.minX + EPSILON_INSET,
            boundingBox.minY + EPSILON_INSET,
            boundingBox.minZ + EPSILON_INSET
        )
        val max = BlockPos.containing(
            boundingBox.maxX - EPSILON_INSET,
            boundingBox.maxY - EPSILON_INSET,
            boundingBox.maxZ - EPSILON_INSET
        )

        for (pos in BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(pos).`is`(Blocks.COBWEB)) {
                return true
            }
        }
        return false
    }

    fun applyCobwebDrag(state: FootballPhysicsState) {
        state.linearVelocity = Vec3(
            state.linearVelocity.x * FootballPhysicsConfig.COBWEB_HORIZONTAL_DRAG,
            state.linearVelocity.y * FootballPhysicsConfig.COBWEB_VERTICAL_DRAG,
            state.linearVelocity.z * FootballPhysicsConfig.COBWEB_HORIZONTAL_DRAG
        )
        state.angularVelocity = state.angularVelocity.scale(FootballPhysicsConfig.COBWEB_SPIN_DRAG)
        state.inCobweb = true
    }

    private const val EPSILON_INSET = 1.0e-3
}
