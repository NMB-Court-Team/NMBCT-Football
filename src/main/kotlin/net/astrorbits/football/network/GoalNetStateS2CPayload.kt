package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * S2C: 同步某个球网实体的节点形变。
 *
 * [relativePositions] 为相对实体原点的节点偏移，长度 = cols*rows*3。
 */
class GoalNetStateS2CPayload(
    val entityId: Int,
    val cols: Int,
    val rows: Int,
    val relativePositions: FloatArray,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GoalNetStateS2CPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<GoalNetStateS2CPayload> =
            CustomPacketPayload.Type(NMBCTFootball.id("goal_net_state"))

        val CODEC: StreamCodec<FriendlyByteBuf, GoalNetStateS2CPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeVarInt(payload.entityId)
                buf.writeVarInt(payload.cols)
                buf.writeVarInt(payload.rows)
                val arr = payload.relativePositions
                buf.writeVarInt(arr.size)
                for (v in arr) buf.writeFloat(v)
            },
            { buf ->
                val entityId = buf.readVarInt()
                val cols = buf.readVarInt()
                val rows = buf.readVarInt()
                val size = buf.readVarInt().coerceIn(0, MAX_FLOATS)
                val arr = FloatArray(size) { buf.readFloat() }
                GoalNetStateS2CPayload(entityId, cols, rows, arr)
            }
        )

        /** 单包节点 float 上限（12x12 节点 * 3）保护，防止异常包过大。 */
        private const val MAX_FLOATS: Int = 12 * 12 * 3
    }
}
