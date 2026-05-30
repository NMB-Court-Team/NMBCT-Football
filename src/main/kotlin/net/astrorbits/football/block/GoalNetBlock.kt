package net.astrorbits.football.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty

/**
 * 球门的网，具有特殊的物理特性，球碰到此方块不会反弹，而是会贴着墙面缓慢往下滑，直到落地
 */
class GoalNetBlock(properties: Properties) : Block(properties) {

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(UP, false)
                .setValue(DOWN, false)
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(UP, DOWN, NORTH, SOUTH, EAST, WEST)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        return computeConnections(context.level, context.clickedPos, defaultBlockState())
    }

    override fun updateShape(
        state: BlockState,
        level: LevelReader,
        tickAccess: ScheduledTickAccess,
        pos: BlockPos,
        direction: Direction,
        neighborPos: BlockPos,
        neighborState: BlockState,
        random: RandomSource
    ): BlockState {
        return state.setValue(propertyFor(direction), neighborState.`is`(this))
    }

    private fun computeConnections(level: LevelReader, pos: BlockPos, state: BlockState): BlockState {
        var result = state
        for (direction in Direction.entries) {
            result = result.setValue(propertyFor(direction), canConnectTo(level, pos, direction))
        }
        return result
    }

    private fun canConnectTo(level: LevelReader, pos: BlockPos, direction: Direction): Boolean {
        return level.getBlockState(pos.relative(direction)).`is`(this)
    }

    private fun propertyFor(direction: Direction): BooleanProperty {
        return when (direction) {
            Direction.UP -> UP
            Direction.DOWN -> DOWN
            Direction.NORTH -> NORTH
            Direction.SOUTH -> SOUTH
            Direction.EAST -> EAST
            Direction.WEST -> WEST
        }
    }

    companion object {
        val UP: BooleanProperty = BooleanProperty.create("up")
        val DOWN: BooleanProperty = BooleanProperty.create("down")
        val NORTH: BooleanProperty = BooleanProperty.create("north")
        val SOUTH: BooleanProperty = BooleanProperty.create("south")
        val EAST: BooleanProperty = BooleanProperty.create("east")
        val WEST: BooleanProperty = BooleanProperty.create("west")
    }
}
