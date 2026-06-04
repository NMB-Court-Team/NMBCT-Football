package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 比赛暂停/继续 Banner */
data class MatchPauseS2CPayload(val paused: Boolean) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<MatchPauseS2CPayload> =
            CustomPacketPayload.Type(NMBCTFootball.id("match_pause"))
        val CODEC: StreamCodec<FriendlyByteBuf, MatchPauseS2CPayload> = StreamCodec.composite(
            ByteBufCodecs.BOOL, MatchPauseS2CPayload::paused,
            ::MatchPauseS2CPayload,
        )
    }
}
