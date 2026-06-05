package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 区域违规警告（仅发给违规玩家本人）。secondsRemaining 为 0 表示清除警告。 */
data class SetPieceAreaViolationS2CPayload(
    val areaNameKey: String,
    val secondsRemaining: Int,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<SetPieceAreaViolationS2CPayload> =
            CustomPacketPayload.Type(NMBCTFootball.id("set_piece_area_violation"))
        val CODEC: StreamCodec<FriendlyByteBuf, SetPieceAreaViolationS2CPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SetPieceAreaViolationS2CPayload::areaNameKey,
            ByteBufCodecs.VAR_INT, SetPieceAreaViolationS2CPayload::secondsRemaining,
            ::SetPieceAreaViolationS2CPayload,
        )
    }
}
