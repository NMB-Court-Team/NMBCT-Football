package net.astrorbits.football.physics

import com.mojang.serialization.Codec
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.Vec3

/**
 * 球网质点网格的 Verlet 状态 NBT 序列化，供 [net.astrorbits.football.GoalNetEntity] 存档使用。
 */
object GoalNetMeshNbt {
    const val MESH_POS = "mesh_pos"
    const val MESH_PREV = "mesh_prev"
    const val ACTIVE_TICKS = "active_ticks"

    fun write(mesh: GoalNetMesh, origin: Vec3, activeTicks: Int, output: ValueOutput) {
        val posList = output.list(MESH_POS, Codec.DOUBLE)
        val prevList = output.list(MESH_PREV, Codec.DOUBLE)
        mesh.writeRelativeSimulationState(origin, posList::add, prevList::add)
        output.putInt(ACTIVE_TICKS, activeTicks)
    }

    /**
     * 将存档中的节点状态写回 [mesh]。
     *
     * @return 存档中的 [ACTIVE_TICKS]；若无网格数据则返回 `null`（调用方保留默认活跃 tick）。
     */
    fun read(input: ValueInput, mesh: GoalNetMesh, origin: Vec3): Int? {
        val pos = readDoubleList(input, MESH_POS) ?: return null
        val prev = readDoubleList(input, MESH_PREV) ?: return null
        if (!mesh.restoreRelativeSimulationState(origin, pos, prev)) {
            return null
        }
        return input.getIntOr(ACTIVE_TICKS, 0)
    }

    private fun readDoubleList(input: ValueInput, key: String): List<Double>? {
        val list = input.list(key, Codec.DOUBLE).orElse(null)?.stream()?.toList()
        if (list.isNullOrEmpty()) return null
        return list
    }
}
