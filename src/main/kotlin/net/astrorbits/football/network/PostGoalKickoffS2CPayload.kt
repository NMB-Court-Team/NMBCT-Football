package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.TeamSide
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** S2C: 进球后重新开球（失分方发球，20s 倒计时，无选择阶段，无中央 HUD） */
data class PostGoalKickoffS2CPayload(
    val kickoffTeam: TeamSide,
    val isKickoffTeam: Boolean,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<PostGoalKickoffS2CPayload> = CustomPacketPayload.Type(NMBCTFootball.id("post_goal_kickoff"))
        val CODEC: StreamCodec<FriendlyByteBuf, PostGoalKickoffS2CPayload> = StreamCodec.composite(
            TeamSide.STREAM_CODEC, PostGoalKickoffS2CPayload::kickoffTeam,
            ByteBufCodecs.BOOL, PostGoalKickoffS2CPayload::isKickoffTeam,
            ::PostGoalKickoffS2CPayload,
        )
    }
}
