package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.TeamSide
import net.minecraft.core.UUIDUtil
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.*

/** S2C：球员被罚下（全员左侧广播；被罚下者另显示居中红牌 HUD）。 */
data class PlayerSendOffS2CPayload(
    val sentOffPlayerUuid: UUID,
    val playerName: String,
    val team: TeamSide,
    val expireAtTimerTicks: Int,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<PlayerSendOffS2CPayload> =
            CustomPacketPayload.Type(NMBCTFootball.id("player_send_off"))
        val CODEC: StreamCodec<FriendlyByteBuf, PlayerSendOffS2CPayload> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PlayerSendOffS2CPayload::sentOffPlayerUuid,
            ByteBufCodecs.STRING_UTF8, PlayerSendOffS2CPayload::playerName,
            TeamSide.STREAM_CODEC, PlayerSendOffS2CPayload::team,
            ByteBufCodecs.INT, PlayerSendOffS2CPayload::expireAtTimerTicks,
            ::PlayerSendOffS2CPayload,
        )
    }
}
