package net.astrorbits.football.util

import net.minecraft.core.BlockPos
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/**
 * 球网实体四个锚点方块的存档读写。
 */
object GoalNetAnchorsNbt {
    const val ANCHORS = "anchors"
    private const val REQUIRED_COUNT = 4

    fun write(anchors: List<BlockPos>, output: ValueOutput) {
        if (anchors.size != REQUIRED_COUNT) return
        val list = output.list(ANCHORS, BlockPos.CODEC)
        for (pos in anchors) {
            list.add(pos)
        }
    }

    fun read(input: ValueInput): List<BlockPos>? {
        readList(input)?.let { return it }
        return readLegacyFlat(input)
    }

    private fun readList(input: ValueInput): List<BlockPos>? {
        val list = input.list(ANCHORS, BlockPos.CODEC).orElse(null)?.stream()?.toList()
        if (list == null || list.size != REQUIRED_COUNT) return null
        return list
    }

    /** 兼容旧版 `a0x` / `a0y` / `a0z` 扁平键。 */
    private fun readLegacyFlat(input: ValueInput): List<BlockPos>? {
        val list = ArrayList<BlockPos>(REQUIRED_COUNT)
        for (n in 0 until REQUIRED_COUNT) {
            val x = input.getIntOr("a${n}x", Int.MIN_VALUE)
            val y = input.getIntOr("a${n}y", Int.MIN_VALUE)
            val z = input.getIntOr("a${n}z", Int.MIN_VALUE)
            if (x == Int.MIN_VALUE) return null
            list.add(BlockPos(x, y, z))
        }
        return list
    }
}
