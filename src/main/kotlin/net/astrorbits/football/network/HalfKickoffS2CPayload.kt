package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.TeamSide
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 半场开球（下半场 / 加时上下半场），带阶段名 + 发球方 */
data class HalfKickoffS2CPayload(
    val kickoffTeam: TeamSide,
    val isKickoffTeam: Boolean,
    val phaseKey: String,       // 阶段翻译键
    val teamAName: String,
    val teamBName: String,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<HalfKickoffS2CPayload> = CustomPacketPayload.Type(NMBCTFootball.id("half_kickoff"))
        val CODEC: StreamCodec<FriendlyByteBuf, HalfKickoffS2CPayload> = StreamCodec.composite(
            TeamSide.STREAM_CODEC, HalfKickoffS2CPayload::kickoffTeam,
            ByteBufCodecs.BOOL, HalfKickoffS2CPayload::isKickoffTeam,
            ByteBufCodecs.STRING_UTF8, HalfKickoffS2CPayload::phaseKey,
            ByteBufCodecs.STRING_UTF8, HalfKickoffS2CPayload::teamAName,
            ByteBufCodecs.STRING_UTF8, HalfKickoffS2CPayload::teamBName,
            ::HalfKickoffS2CPayload,
        )
    }
}
