package net.astrorbits.football.util

import net.minecraft.core.Direction

data class CompositeDirection(val dir1: Direction, val dir2: Direction) : Set<Direction> by setOf(dir1, dir2) {
    init {
        require(dir1.axis != dir2.axis) { "Directions must be perpendicular" }
    }

    val axes: Set<Direction.Axis> = setOf(dir1.axis, dir2.axis)

    /**
     * 判断是否包含某轴向的任一方向
     */
    fun isAnyAlongAxis(axis: Direction.Axis): Boolean {
        return axis in axes
    }

    /**
     * 判断与另一个组合方向是否完全在同一平面上
     */
    fun isInSamePlane(other: CompositeDirection): Boolean {
        return axes == other.axes
    }

    fun isPerpendicularTo(axis: Direction.Axis): Boolean {
        return axis !in axes
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompositeDirection) return false

        if (dir1 != other.dir1 && dir1 != other.dir2) return false
        if (dir2 != other.dir1 && dir2 != other.dir2) return false

        return true
    }

    override fun hashCode(): Int {
        val d1 = if (dir1 < dir2) dir1 else dir2
        val d2 = if (d1 == dir1) dir2 else dir1
        var result = d1.hashCode()
        result = 31 * result + d2.hashCode()
        return result
    }

    companion object {
        /**
         * 所有两个不同轴向的方向组合，共12个
         */
        val ENTRIES = setOf(
            Direction.UP combine Direction.NORTH,
            Direction.UP combine Direction.SOUTH,
            Direction.UP combine Direction.EAST,
            Direction.UP combine Direction.WEST,
            Direction.DOWN combine Direction.NORTH,
            Direction.DOWN combine Direction.SOUTH,
            Direction.DOWN combine Direction.EAST,
            Direction.DOWN combine Direction.WEST,
            Direction.NORTH combine Direction.EAST,
            Direction.NORTH combine Direction.WEST,
            Direction.SOUTH combine Direction.EAST,
            Direction.SOUTH combine Direction.WEST
        )
    }
}

infix fun Direction.combine(other: Direction): CompositeDirection {
    return CompositeDirection(this, other)
}
