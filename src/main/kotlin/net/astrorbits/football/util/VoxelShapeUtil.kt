package net.astrorbits.football.util

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.Axis
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

fun Direction.isHorizontal(): Boolean {
    return this == Direction.WEST || this == Direction.EAST || this == Direction.NORTH || this == Direction.SOUTH
}

fun Direction.isVertical(): Boolean {
    return this == Direction.UP || this == Direction.DOWN
}

object VoxelShapeUtil {
    private const val CUBOID_UNIT_SCALE = 1.0 / 16.0

    /**
     * 根据BlockBench里的模型坐标创建[VoxelShape]对象
     */
    fun blockBenchCuboid(startX: Double, startY: Double, startZ: Double, sizeX: Double, sizeY: Double, sizeZ: Double): VoxelShape {
        return Shapes.create(blockBenchBox(startX, startY, startZ, sizeX, sizeY, sizeZ))
    }

    /**
     * 根据BlockBench里的模型坐标创建[AABB]对象
     */
    fun blockBenchBox(startX: Double, startY: Double, startZ: Double, sizeX: Double, sizeY: Double, sizeZ: Double): AABB {
        return AABB(
            startX * CUBOID_UNIT_SCALE,
            startY * CUBOID_UNIT_SCALE,
            startZ * CUBOID_UNIT_SCALE,
            (startX + sizeX) * CUBOID_UNIT_SCALE,
            (startY + sizeY) * CUBOID_UNIT_SCALE,
            (startZ + sizeZ) * CUBOID_UNIT_SCALE
        )
    }

    /**
     * 将基准朝向为[baseDirection]的[VoxelShape]对象旋转到目标朝向
     *
     * 此方法会将模型从一个水平朝向转到另一个水平朝向
     * @param targetDirection 目标朝向
     * @param baseDirection 基准朝向
     * @return 旋转到[targetDirection]朝向的[VoxelShape]
     */
    fun VoxelShape.rotateHorizontal(targetDirection: Direction, baseDirection: Direction = Direction.NORTH): VoxelShape {
        require(targetDirection.isHorizontal() && baseDirection.isHorizontal())
        val baseTurns = baseDirection.get2DDataValue()
        val targetTurns = targetDirection.get2DDataValue()
        val turnCount = (targetTurns + 4 - baseTurns) % 4

        var result = this
        repeat(turnCount) {
            result = result.rotate90Y()
        }
        return result
    }

    /**
     * 将基准朝向为[baseDirection]的[VoxelShape]对象旋转到目标朝向
     *
     * 此方法会将模型从一个水平朝向转到一个竖直朝向
     * @param targetDirection 目标朝向
     * @param baseDirection 基准朝向
     * @return 旋转到[targetDirection]朝向的[VoxelShape]
     */
    fun VoxelShape.rotateToVertical(targetDirection: Direction, baseDirection: Direction = Direction.NORTH): VoxelShape {
        require(targetDirection.isVertical() && baseDirection.isHorizontal())
        return when (targetDirection) {
            Direction.UP -> when (baseDirection) {
                Direction.NORTH -> rotate90X(false)
                Direction.EAST -> rotate90Z(false)
                Direction.SOUTH -> rotate90X(true)
                Direction.WEST -> rotate90Z(true)
                else -> this
            }
            Direction.DOWN -> when (baseDirection) {
                Direction.NORTH -> rotate90X(true)
                Direction.EAST -> rotate90Z(true)
                Direction.SOUTH -> rotate90X(false)
                Direction.WEST -> rotate90Z(false)
                else -> this
            }
            else -> this
        }
    }

    /**
     * 将形状沿着法向量为[normal]的平面镜像
     * @param normal 法向量，例如当法向量为[Axis.Y]时，代表沿着`y=0.5`平面镜像
     */
    fun VoxelShape.mirror(normal: Axis): VoxelShape {
        var result = Shapes.empty()
        for (box in this.toAabbs()) {
            val x1 = if (normal == Axis.X) 1 - box.minX else box.minX
            val y1 = if (normal == Axis.Y) 1 - box.minY else box.minY
            val z1 = if (normal == Axis.Z) 1 - box.minZ else box.minZ
            val x2 = if (normal == Axis.X) 1 - box.maxX else box.maxX
            val y2 = if (normal == Axis.Y) 1 - box.maxY else box.maxY
            val z2 = if (normal == Axis.Z) 1 - box.maxZ else box.maxZ
            result = Shapes.or(
                result,
                simpleCuboid(x1, y1, z1, x2, y2, z2)
            )
        }
        return result
    }

    /**
     * 用两个角点坐标创建[VoxelShape]，角点不一定是最小角点和最大角点
     */
    fun simpleCuboid(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): VoxelShape {
        return Shapes.box(
            min(x1, x2),
            min(y1, y2),
            min(z1, z2),
            max(x1, x2),
            max(y1, y2),
            max(z1, z2)
        )
    }

    /**
     * 将[VoxelShape]绕Y轴(直线`x=z=0.5`)旋转90°
     *
     * **快速理解旋转方向:** 从y+往y-看，顺时针旋转90°，例如North(z-)会被转到East(x+)
     */
    fun VoxelShape.rotate90Y(clockwise: Boolean = true): VoxelShape {
        var result = Shapes.empty()
        for (box in this.toAabbs()) {
            val x1 = if (clockwise) 1 - box.minZ else box.minZ
            val y1 = box.minY
            val z1 = if (clockwise) box.minX else 1 - box.minX
            val x2 = if (clockwise) 1 - box.maxZ else box.maxZ
            val y2 = box.maxY
            val z2 = if (clockwise) box.maxX else 1 - box.maxX
            result = Shapes.or(result, simpleCuboid(x1, y1, z1, x2, y2, z2))
        }
        return result.optimize()
    }

    /**
     * 将[VoxelShape]绕X轴(直线`y=z=0.5`)旋转90°
     *
     * **快速理解旋转方向:** 从x+往x-看，顺时针旋转90°，例如North(z-)会被转到Down(y-)
     */
    fun VoxelShape.rotate90X(clockwise: Boolean = true): VoxelShape {
        var result = Shapes.empty()
        for (box in this.toAabbs()) {
            val x1 = box.minX
            val y1 = if (clockwise) box.minZ else 1 - box.minZ
            val z1 = if (clockwise) 1 - box.minY else box.minY
            val x2 = box.maxX
            val y2 = if (clockwise) box.maxZ else 1 - box.maxZ
            val z2 = if (clockwise) 1 - box.maxY else box.maxY
            result = Shapes.or(result, simpleCuboid(x1, y1, z1, x2, y2, z2))
        }
        return result.optimize()
    }

    /**
     * 将[VoxelShape]绕Z轴(直线`x=y=0.5`)旋转90°
     *
     * **快速理解旋转方向:** 从z+往z-看，顺时针旋转90°，例如Up(y+)会被转到East(x+)
     */
    fun VoxelShape.rotate90Z(clockwise: Boolean = true): VoxelShape {
        var result = Shapes.empty()
        for (box in this.toAabbs()) {
            val x1 = if (clockwise) box.minY else 1 - box.minY
            val y1 = if (clockwise) 1 - box.minX else box.minX
            val z1 = box.minZ
            val x2 = if (clockwise) box.maxY else 1 - box.maxY
            val y2 = if (clockwise) 1 - box.maxX else box.maxX
            val z2 = box.maxZ
            result = Shapes.or(result, simpleCuboid(x1, y1, z1, x2, y2, z2))
        }
        return result.optimize()
    }

    /**
     * 为基准方向是[baseDirection]的[VoxelShape]创建水平四向的[VoxelShape]
     *
     * **注意:** 对于带朝向的方块，模型的位置贴着North的时候，朝向一般是South，对于其他朝向同理，
     * 因此在创建完之后应当进行如下操作：
     * ```kotlin
     * VoxelShapeUtil.blockBenchCuboid(...)
     *     .generateHorizontalFacingShapes(baseDirection)
     *     .mapKeys { it.key.opposite }
     * ```
     * 将键的朝向重映射到与之相对的朝向
     * @param baseDirection [VoxelShape]的基准朝向，必须为水平朝向
     */
    fun VoxelShape.generateHorizontalFacingShapes(baseDirection: Direction = Direction.NORTH): Map<Direction, VoxelShape> {
        require(baseDirection.isHorizontal())
        val result = hashMapOf<Direction, VoxelShape>()
        for (direction in Direction.entries) {
            if (direction.isVertical()) continue
            result[direction] = this.rotateHorizontal(direction, baseDirection)
        }
        return result
    }

    /**
     * 为基准方向是[baseDirection]的[VoxelShape]创建六个方向的[VoxelShape]
     *
     * **注意:** 对于带朝向的方块，模型的位置贴着North的时候，朝向一般是South，对于其他朝向同理，
     * 因此在创建完之后应当进行如下操作：
     * ```kotlin
     * VoxelShapeUtil.blockBenchCuboid(...)
     *     .generateHorizontalFacingShapes(baseDirection)
     *     .mapKeys { it.key.opposite }
     * ```
     * 将键的朝向重映射到与之相对的朝向
     * @param baseDirection [VoxelShape]的基准朝向，必须为水平朝向
     */
    fun VoxelShape.generateAllFacingShapes(baseDirection: Direction = Direction.NORTH): Map<Direction, VoxelShape> {
        require(baseDirection.isHorizontal())
        val result = hashMapOf<Direction, VoxelShape>()
        for (direction in Direction.entries) {
            if (direction.isVertical()) {
                result[direction] = this.rotateToVertical(direction, baseDirection)
            } else {
                result[direction] = this.rotateHorizontal(direction, baseDirection)
            }
        }
        return result
    }

    /**
     * 将形状沿着方块网格线分割
     * @return 各个网格分割出来的形状，键为网格坐标，值为网格对应的形状
     */
    fun VoxelShape.splitByBlockGrid(): Map<BlockPos, VoxelShape> {
        val result = mutableMapOf<BlockPos, VoxelShape>()

        val bounds = this.bounds()

        val minX = floor(bounds.minX).toInt()
        val minY = floor(bounds.minY).toInt()
        val minZ = floor(bounds.minZ).toInt()
        val maxX = ceil(bounds.maxX).toInt()
        val maxY = ceil(bounds.maxY).toInt()
        val maxZ = ceil(bounds.maxZ).toInt()

        for (x in minX until maxX) {
            for (y in minY until maxY) {
                for (z in minZ until maxZ) {
                    val blockShape = Shapes.box(
                        x.toDouble(), y.toDouble(), z.toDouble(),
                        (x + 1).toDouble(), (y + 1).toDouble(), (z + 1).toDouble()
                    )
                    val intersection = intersect(this, blockShape)
                    val localShape = intersection.move(-x.toDouble(), -y.toDouble(), -z.toDouble())
                    result[BlockPos(x, y, z)] = localShape.optimize()
                }
            }
        }

        return result
    }

    /**
     * 创建一个最小角点坐标为[pos]的完整方块的形状
     */
    fun fullCube(pos: BlockPos = BlockPos.ZERO): VoxelShape {
        return Shapes.box(
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            (pos.x + 1).toDouble(), (pos.y + 1).toDouble(), (pos.z + 1).toDouble()
        )
    }

    /**
     * 创建由多个完整方块组合的形状
     * @param positions 多个完整方块的最小角点坐标集合
     */
    fun multiFullCube(positions: Collection<BlockPos>): VoxelShape {
        var result = Shapes.empty()
        for (pos in positions) {
            result = Shapes.or(result, fullCube(pos))
        }
        return result
    }

    fun union(vararg shapes: VoxelShape): VoxelShape {
        if (shapes.isEmpty()) return Shapes.empty()
        val first = shapes[0]
        val others = shapes.drop(1)
        return Shapes.or(first, *others.toTypedArray())
    }

    fun union(shapes: Collection<VoxelShape>): VoxelShape {
        if (shapes.isEmpty()) return Shapes.empty()
        val first = shapes.first()
        val others = shapes.drop(1)
        return Shapes.or(first, *others.toTypedArray())
    }

    /**
     * 取两个形状共有的部分（取交集）
     */
    fun intersect(first: VoxelShape, second: VoxelShape): VoxelShape {
        return Shapes.join(first, second, BooleanOp.AND)
    }

    /**
     * 取第一个形状有，第二个形状没有的部分
     */
    fun firstOnly(first: VoxelShape, second: VoxelShape): VoxelShape {
        return Shapes.join(first, second, BooleanOp.ONLY_FIRST)
    }

    /**
     * 取第二个形状有，第一个形状没有的部分
     */
    fun secondOnly(first: VoxelShape, second: VoxelShape): VoxelShape {
        return Shapes.join(first, second, BooleanOp.ONLY_SECOND)
    }

    /**
     * 获取方块的碰撞箱，碰撞箱的坐标会偏移到方块实际的位置
     */
    fun BlockGetter.getBlockCollisionShape(pos: BlockPos): VoxelShape {
        return getBlockState(pos).getCollisionShape(this, pos).move(pos)
    }
}
