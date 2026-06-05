package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.FreeKickFoulReason
import net.astrorbits.football.match.FreeKickType
import net.astrorbits.football.match.TeamSide
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 任意球判罚（类型、犯规原因、发球方） */
data class FreeKickAwardS2CPayload(
    val freeKickType: FreeKickType,
    val foulReason: FreeKickFoulReason,
    val foulingPlayerName: String,
    val foulingTeam: TeamSide,
    val restartTeam: TeamSide,
    val ballX: Double,
    val ballY: Double,
    val ballZ: Double,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<FreeKickAwardS2CPayload> =
            CustomPacketPayload.Type(NMBCTFootball.id("free_kick_award"))
        val CODEC: StreamCodec<FriendlyByteBuf, FreeKickAwardS2CPayload> = StreamCodec.composite(
            FreeKickType.STREAM_CODEC, FreeKickAwardS2CPayload::freeKickType,
            FreeKickFoulReason.STREAM_CODEC, FreeKickAwardS2CPayload::foulReason,
            ByteBufCodecs.STRING_UTF8, FreeKickAwardS2CPayload::foulingPlayerName,
            TeamSide.STREAM_CODEC, FreeKickAwardS2CPayload::foulingTeam,
            TeamSide.STREAM_CODEC, FreeKickAwardS2CPayload::restartTeam,
            ByteBufCodecs.DOUBLE, FreeKickAwardS2CPayload::ballX,
            ByteBufCodecs.DOUBLE, FreeKickAwardS2CPayload::ballY,
            ByteBufCodecs.DOUBLE, FreeKickAwardS2CPayload::ballZ,
            ::FreeKickAwardS2CPayload,
        )
    }
}
