package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.PenaltyKickPhase
import net.astrorbits.football.match.TeamSide
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.*

/** S2C: 点球大战状态同步 */
data class PenaltyShootoutSyncS2CPayload(
    val active: Boolean,
    val penaltyScoreA: Int,
    val penaltyScoreB: Int,
    val suddenDeath: Boolean,
    val totalKicksTaken: Int,
    val currentKickerTeam: TeamSide,
    val kickerName: String,
    val currentKickerUuid: UUID?,
    val kickPhase: PenaltyKickPhase,
    val penaltyGoalTeam: TeamSide,
    val activeDefendingTeam: TeamSide,
    val firstKickTeam: TeamSide,
    /** 单轮罚球出结果后的球延迟复位宽限（与 [MatchState.postGoalResetPending] 一致）。 */
    val ballGracePending: Boolean,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<PenaltyShootoutSyncS2CPayload> =
            CustomPacketPayload.Type(NMBCTFootball.id("penalty_shootout_sync"))

        private val PHASE_CODEC: StreamCodec<FriendlyByteBuf, PenaltyKickPhase> = StreamCodec.of(
            { buf, phase -> buf.writeInt(phase.ordinal) },
            { buf -> PenaltyKickPhase.entries[buf.readInt()] },
        )

        val CODEC: StreamCodec<FriendlyByteBuf, PenaltyShootoutSyncS2CPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeBoolean(payload.active)
                buf.writeInt(payload.penaltyScoreA)
                buf.writeInt(payload.penaltyScoreB)
                buf.writeBoolean(payload.suddenDeath)
                buf.writeInt(payload.totalKicksTaken)
                TeamSide.STREAM_CODEC.encode(buf, payload.currentKickerTeam)
                buf.writeUtf(payload.kickerName)
                buf.writeBoolean(payload.currentKickerUuid != null)
                payload.currentKickerUuid?.let { buf.writeUUID(it) }
                buf.writeInt(payload.kickPhase.ordinal)
                TeamSide.STREAM_CODEC.encode(buf, payload.penaltyGoalTeam)
                TeamSide.STREAM_CODEC.encode(buf, payload.activeDefendingTeam)
                TeamSide.STREAM_CODEC.encode(buf, payload.firstKickTeam)
                buf.writeBoolean(payload.ballGracePending)
            },
            { buf ->
                PenaltyShootoutSyncS2CPayload(
                    active = buf.readBoolean(),
                    penaltyScoreA = buf.readInt(),
                    penaltyScoreB = buf.readInt(),
                    suddenDeath = buf.readBoolean(),
                    totalKicksTaken = buf.readInt(),
                    currentKickerTeam = TeamSide.STREAM_CODEC.decode(buf),
                    kickerName = buf.readUtf(),
                    currentKickerUuid = if (buf.readBoolean()) buf.readUUID() else null,
                    kickPhase = PenaltyKickPhase.entries[buf.readInt()],
                    penaltyGoalTeam = TeamSide.STREAM_CODEC.decode(buf),
                    activeDefendingTeam = TeamSide.STREAM_CODEC.decode(buf),
                    firstKickTeam = TeamSide.STREAM_CODEC.decode(buf),
                    ballGracePending = buf.readBoolean(),
                )
            },
        )
    }
}
