package net.astrorbits.football.item

import net.minecraft.core.BlockPos
import java.util.*

/**
 * 记录每个玩家手持球网连接器时已选择的锚点序列（服务端）。
 */
object GoalNetConnectorState {
    private val selections = HashMap<UUID, MutableList<BlockPos>>()

    fun getPoints(player: UUID): List<BlockPos> = selections[player] ?: emptyList()

    /** 追加一个锚点。若已存在则忽略并返回 false。返回当前数量。 */
    fun addPoint(player: UUID, pos: BlockPos): Int {
        val list = selections.getOrPut(player) { ArrayList(4) }
        if (list.contains(pos)) return list.size
        list.add(pos)
        return list.size
    }

    fun contains(player: UUID, pos: BlockPos): Boolean = selections[player]?.contains(pos) == true

    fun clear(player: UUID) {
        selections.remove(player)
    }
}
