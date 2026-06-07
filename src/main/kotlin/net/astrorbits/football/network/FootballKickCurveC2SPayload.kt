package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** 蓄力射门后窗口内的视角偏航输入（度，正=右转）。 */
data class FootballKickCurveC2SPayload(
    val curveYawDeltaDeg: Float,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<FootballKickCurveC2SPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<FootballKickCurveC2SPayload>(NMBCTFootball.id("football_kick_curve"))

        val CODEC: StreamCodec<FriendlyByteBuf, FootballKickCurveC2SPayload> = StreamCodec.of(
            { buf, payload -> buf.writeFloat(payload.curveYawDeltaDeg) },
            { buf -> FootballKickCurveC2SPayload(curveYawDeltaDeg = buf.readFloat()) },
        )
    }
}
