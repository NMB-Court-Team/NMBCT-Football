package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 同步手持连接器玩家当前已选的锚点方块列表（最多 4 个）。 */
class GoalNetConnectorSelectionS2CPayload(
    val anchorBlocks: List<BlockPos>,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GoalNetConnectorSelectionS2CPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<GoalNetConnectorSelectionS2CPayload> =
            CustomPacketPayload.Type(NMBCTFootball.id("goal_net_connector_selection"))

        val CODEC: StreamCodec<FriendlyByteBuf, GoalNetConnectorSelectionS2CPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeVarInt(payload.anchorBlocks.size.coerceIn(0, MAX_POINTS))
                for (pos in payload.anchorBlocks) {
                    buf.writeBlockPos(pos)
                }
            },
            { buf ->
                val count = buf.readVarInt().coerceIn(0, MAX_POINTS)
                val list = ArrayList<BlockPos>(count)
                repeat(count) { list.add(buf.readBlockPos()) }
                GoalNetConnectorSelectionS2CPayload(list)
            },
        )

        private const val MAX_POINTS = 4
    }
}
