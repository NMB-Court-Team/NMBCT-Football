package net.astrorbits.football.block

import com.mojang.serialization.MapCodec
import net.astrorbits.football.util.VoxelShapeUtil
import net.astrorbits.football.util.VoxelShapeUtil.generateAllFacingShapes
import net.astrorbits.football.util.VoxelShapeUtil.generateHorizontalFacingShapes
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * 球网锚点方块：作为球网四角的固定连接点。
 *
 * 可放置在表面中心或悬空中央。其本身不含连接逻辑，
 * 连接由 [net.astrorbits.football.item.GoalNetConnectorItem] 右键采集四个锚点后完成。
 *
 * 模型与纹理待后续补充。
 */
class GoalNetAnchorBlock(properties: Properties) : Block(properties) {
    init {
        registerDefaultState(defaultBlockState().setValue(POSITION, GoalNetAnchorPosition.CENTER))
    }

    override fun codec() = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(POSITION)
    }

    /**
     * 获取锚点的世界坐标。根据 [POSITION] 的不同，锚点可能位于方块中心、表面或边角。
     * @param pos 方块位置
     * @param state 方块状态，必须是球网锚点方块
     */
    fun getAnchorPos(pos: BlockPos, state: BlockState): Vec3 {
        val anchorPos = state.getValue(POSITION)
        return Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5).add(anchorPos.offset)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        val anchorPos = state.getValue(POSITION)
        return if (anchorPos.isCenter) {
            CENTER_SHAPE
        } else if (anchorPos.isFace) {
            FACE_SHAPES[anchorPos]!!
        } else if (anchorPos.isSide) {
            if (anchorPos.shortName.startsWith("u")) {
                UP_SIDE_SHAPES[anchorPos]!!
            } else if (anchorPos.shortName.startsWith("d")) {
                DOWN_SIDE_SHAPES[anchorPos]!!
            } else {
                MIDDLE_SIDE_SHAPES[anchorPos]!!
            }
        } else {
            if (anchorPos.shortName.startsWith("u")) {
                UP_CORNER_SHAPES[anchorPos]!!
            } else {
                DOWN_CORNER_SHAPES[anchorPos]!!
            }
        }
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        if (context.player?.isShiftKeyDown == true) {
            // 按住Shift时放在中央
            return defaultBlockState().setValue(POSITION, GoalNetAnchorPosition.CENTER)
        }
        val clickPos = context.clickLocation
        val blockPos = context.clickedPos
        val nearestPos = GoalNetAnchorPosition.entries.minBy { blockPos.center.add(it.offset).distanceToSqr(clickPos) }
        return defaultBlockState().setValue(POSITION, nearestPos)
    }

    companion object {
        val CODEC: MapCodec<GoalNetAnchorBlock> = simpleCodec(::GoalNetAnchorBlock)
        val POSITION: EnumProperty<GoalNetAnchorPosition> = EnumProperty.create("position", GoalNetAnchorPosition::class.java)

        val CENTER_SHAPE = VoxelShapeUtil.blockBenchCuboid(7.0, 7.0, 7.0, 2.0, 2.0, 2.0)
        val FACE_SHAPES: Map<GoalNetAnchorPosition, VoxelShape> =
            VoxelShapeUtil.blockBenchCuboid(7.0, 7.0, 0.0, 2.0, 2.0, 2.0)
                .generateAllFacingShapes()
                .mapKeys { (dir, _) -> when (dir) {
                    Direction.UP -> GoalNetAnchorPosition.UP
                    Direction.DOWN -> GoalNetAnchorPosition.DOWN
                    Direction.NORTH -> GoalNetAnchorPosition.NORTH
                    Direction.SOUTH -> GoalNetAnchorPosition.SOUTH
                    Direction.EAST -> GoalNetAnchorPosition.EAST
                    Direction.WEST -> GoalNetAnchorPosition.WEST
                } }
        val UP_SIDE_SHAPES: Map<GoalNetAnchorPosition, VoxelShape> =
            VoxelShapeUtil.blockBenchCuboid(7.0, 14.0, 0.0, 2.0, 2.0, 2.0)
                .generateHorizontalFacingShapes()
                .mapKeys { (dir, _) -> when (dir) {
                    Direction.NORTH -> GoalNetAnchorPosition.UP_NORTH
                    Direction.SOUTH -> GoalNetAnchorPosition.UP_SOUTH
                    Direction.EAST -> GoalNetAnchorPosition.UP_EAST
                    Direction.WEST -> GoalNetAnchorPosition.UP_WEST
                    else -> throw AssertionError("Unexpected direction $dir, this should not happen!")
                } }
        val MIDDLE_SIDE_SHAPES: Map<GoalNetAnchorPosition, VoxelShape> =
            VoxelShapeUtil.blockBenchCuboid(0.0, 7.0, 0.0, 2.0, 2.0, 2.0)
                .generateHorizontalFacingShapes()
                .mapKeys { (dir, _) -> when (dir) {
                    Direction.NORTH -> GoalNetAnchorPosition.NORTH_WEST
                    Direction.EAST -> GoalNetAnchorPosition.NORTH_EAST
                    Direction.SOUTH -> GoalNetAnchorPosition.SOUTH_EAST
                    Direction.WEST -> GoalNetAnchorPosition.SOUTH_WEST
                    else -> throw AssertionError("Unexpected direction $dir, this should not happen!")
                } }
        val DOWN_SIDE_SHAPES: Map<GoalNetAnchorPosition, VoxelShape> =
            VoxelShapeUtil.blockBenchCuboid(7.0, 0.0, 0.0, 2.0, 2.0, 2.0)
                .generateHorizontalFacingShapes()
                .mapKeys { (dir, _) -> when (dir) {
                    Direction.NORTH -> GoalNetAnchorPosition.DOWN_NORTH
                    Direction.SOUTH -> GoalNetAnchorPosition.DOWN_SOUTH
                    Direction.EAST -> GoalNetAnchorPosition.DOWN_EAST
                    Direction.WEST -> GoalNetAnchorPosition.DOWN_WEST
                    else -> throw AssertionError("Unexpected direction $dir, this should not happen!")
                } }
        val UP_CORNER_SHAPES: Map<GoalNetAnchorPosition, VoxelShape> =
            VoxelShapeUtil.blockBenchCuboid(0.0, 14.0, 0.0, 2.0, 2.0, 2.0)
                .generateHorizontalFacingShapes()
                .mapKeys { (dir, _) -> when (dir) {
                    Direction.NORTH -> GoalNetAnchorPosition.UP_NORTH_WEST
                    Direction.EAST -> GoalNetAnchorPosition.UP_NORTH_EAST
                    Direction.SOUTH -> GoalNetAnchorPosition.UP_SOUTH_EAST
                    Direction.WEST -> GoalNetAnchorPosition.UP_SOUTH_WEST
                    else -> throw AssertionError("Unexpected direction $dir, this should not happen!")
                } }
        val DOWN_CORNER_SHAPES: Map<GoalNetAnchorPosition, VoxelShape> =
            VoxelShapeUtil.blockBenchCuboid(0.0, 0.0, 0.0, 2.0, 2.0, 2.0)
                .generateHorizontalFacingShapes()
                .mapKeys { (dir, _) -> when (dir) {
                    Direction.NORTH -> GoalNetAnchorPosition.DOWN_NORTH_WEST
                    Direction.EAST -> GoalNetAnchorPosition.DOWN_NORTH_EAST
                    Direction.SOUTH -> GoalNetAnchorPosition.DOWN_SOUTH_EAST
                    Direction.WEST -> GoalNetAnchorPosition.DOWN_SOUTH_WEST
                    else -> throw AssertionError("Unexpected direction $dir, this should not happen!")
                } }
    }
}

@Suppress("UNUSED")
enum class GoalNetAnchorPosition(val shortName: String, val offset: Vec3) : StringRepresentable {
    CENTER("c"),
    // faces
    UP("u", Direction.UP),
    DOWN("d", Direction.DOWN),
    NORTH("n", Direction.NORTH),
    SOUTH("s", Direction.SOUTH),
    EAST("e", Direction.EAST),
    WEST("w", Direction.WEST),
    // sides
    UP_NORTH("un", Direction.UP, Direction.NORTH),
    UP_SOUTH("us", Direction.UP, Direction.SOUTH),
    UP_EAST("ue", Direction.UP, Direction.EAST),
    UP_WEST("uw", Direction.UP, Direction.WEST),
    NORTH_EAST("ne", Direction.NORTH, Direction.EAST),
    NORTH_WEST("nw", Direction.NORTH, Direction.WEST),
    SOUTH_EAST("se", Direction.SOUTH, Direction.EAST),
    SOUTH_WEST("sw", Direction.SOUTH, Direction.WEST),
    DOWN_NORTH("dn", Direction.DOWN, Direction.NORTH),
    DOWN_SOUTH("ds", Direction.DOWN, Direction.SOUTH),
    DOWN_EAST("de", Direction.DOWN, Direction.EAST),
    DOWN_WEST("dw", Direction.DOWN, Direction.WEST),
    // corners
    UP_NORTH_EAST("une", Direction.UP, Direction.NORTH, Direction.EAST),
    UP_NORTH_WEST("unw", Direction.UP, Direction.NORTH, Direction.WEST),
    UP_SOUTH_EAST("use", Direction.UP, Direction.SOUTH, Direction.EAST),
    UP_SOUTH_WEST("usw", Direction.UP, Direction.SOUTH, Direction.WEST),
    DOWN_NORTH_EAST("dne", Direction.DOWN, Direction.NORTH, Direction.EAST),
    DOWN_NORTH_WEST("dnw", Direction.DOWN, Direction.NORTH, Direction.WEST),
    DOWN_SOUTH_EAST("dse", Direction.DOWN, Direction.SOUTH, Direction.EAST),
    DOWN_SOUTH_WEST("dsw", Direction.DOWN, Direction.SOUTH, Direction.WEST),
    ;

    constructor(shortName: String) : this(shortName, Vec3.ZERO)
    constructor(shortName: String, dir: Direction) : this(shortName, dir.unitVec3.scale(0.5))
    constructor(shortName: String, dir1: Direction, dir2: Direction) : this(shortName, dir1.unitVec3.scale(0.5).add(dir2.unitVec3.scale(0.5)))
    constructor(shortName: String, dir1: Direction, dir2: Direction, dir3: Direction) : this(shortName, dir1.unitVec3.scale(0.5).add(dir2.unitVec3.scale(0.5)).add(dir3.unitVec3.scale(0.5)))

    val isCenter: Boolean get() = this == CENTER
    val isFace: Boolean get() = shortName.length == 1 && shortName != "c"
    val isSide: Boolean get() = shortName.length == 2
    val isCorner: Boolean get() = shortName.length == 3

    override fun getSerializedName(): String = shortName
}
