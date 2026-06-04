package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.PenaltyKickPhase
import net.astrorbits.football.match.TeamSide
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
/** S2C: 点球大战状态同步 */
data class PenaltyShootoutSyncS2CPayload(
    val active: Boolean,
    val penaltyScoreA: Int,
    val penaltyScoreB: Int,
    val suddenDeath: Boolean,
    val totalKicksTaken: Int,
    val currentKickerTeam: TeamSide,
    val kickerName: String,
    val kickPhase: PenaltyKickPhase,
    val activeDefendingTeam: TeamSide,
    val firstKickTeam: TeamSide,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<PenaltyShootoutSyncS2CPayload> =
            CustomPacketPayload.Type(NMBCTFootball.id("penalty_shootout_sync"))

        private val PHASE_CODEC: StreamCodec<FriendlyByteBuf, PenaltyKickPhase> = StreamCodec.of(
            { buf, phase -> buf.writeInt(phase.ordinal) },
            { buf -> PenaltyKickPhase.entries[buf.readInt()] },
        )

        val CODEC: StreamCodec<FriendlyByteBuf, PenaltyShootoutSyncS2CPayload> = StreamCodec.composite(
            ByteBufCodecs.BOOL, PenaltyShootoutSyncS2CPayload::active,
            ByteBufCodecs.INT, PenaltyShootoutSyncS2CPayload::penaltyScoreA,
            ByteBufCodecs.INT, PenaltyShootoutSyncS2CPayload::penaltyScoreB,
            ByteBufCodecs.BOOL, PenaltyShootoutSyncS2CPayload::suddenDeath,
            ByteBufCodecs.INT, PenaltyShootoutSyncS2CPayload::totalKicksTaken,
            TeamSide.STREAM_CODEC, PenaltyShootoutSyncS2CPayload::currentKickerTeam,
            ByteBufCodecs.STRING_UTF8, PenaltyShootoutSyncS2CPayload::kickerName,
            PHASE_CODEC, PenaltyShootoutSyncS2CPayload::kickPhase,
            TeamSide.STREAM_CODEC, PenaltyShootoutSyncS2CPayload::activeDefendingTeam,
            TeamSide.STREAM_CODEC, PenaltyShootoutSyncS2CPayload::firstKickTeam,
            ::PenaltyShootoutSyncS2CPayload,
        )
    }
}
