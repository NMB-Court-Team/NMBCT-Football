package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.match.GoalKickPhase
import net.astrorbits.football.match.PenaltyKickPhase
import net.astrorbits.football.match.SetPieceKind
import net.astrorbits.football.match.TeamSide
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** S2C: 同步当前定位球状态供客户端操作限制预判。 */
data class SetPieceStateS2CPayload(
    val kind: SetPieceKind,
    val restartTeam: TeamSide?,
    val goalKickPhase: GoalKickPhase?,
    val goalKickPickerUuid: UUID?,
    val throwInTakerUuid: UUID?,
    val movementFrozen: Boolean,
    val ballPos: Vec3?,
    val defendingSide: TeamSide?,
    val penaltyKickerUuid: UUID?,
    val penaltyKickPhase: PenaltyKickPhase? = null,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val CLEAR = SetPieceStateS2CPayload(
            kind = SetPieceKind.NONE,
            restartTeam = null,
            goalKickPhase = null,
            goalKickPickerUuid = null,
            throwInTakerUuid = null,
            movementFrozen = false,
            ballPos = null,
            defendingSide = null,
            penaltyKickerUuid = null,
            penaltyKickPhase = null,
        )

        val TYPE: CustomPacketPayload.Type<SetPieceStateS2CPayload> =
            CustomPacketPayload.Type(NMBCTFootball.id("set_piece_state"))

        val CODEC: StreamCodec<FriendlyByteBuf, SetPieceStateS2CPayload> = StreamCodec.of(
            { buf, payload ->
                SetPieceKind.STREAM_CODEC.encode(buf, payload.kind)
                buf.writeBoolean(payload.restartTeam != null)
                payload.restartTeam?.let { TeamSide.STREAM_CODEC.encode(buf, it) }
                buf.writeBoolean(payload.goalKickPhase != null)
                payload.goalKickPhase?.let { GoalKickPhase.STREAM_CODEC.encode(buf, it) }
                buf.writeBoolean(payload.goalKickPickerUuid != null)
                payload.goalKickPickerUuid?.let { buf.writeUUID(it) }
                buf.writeBoolean(payload.throwInTakerUuid != null)
                payload.throwInTakerUuid?.let { buf.writeUUID(it) }
                buf.writeBoolean(payload.movementFrozen)
                buf.writeBoolean(payload.ballPos != null)
                payload.ballPos?.let {
                    buf.writeDouble(it.x)
                    buf.writeDouble(it.y)
                    buf.writeDouble(it.z)
                }
                buf.writeBoolean(payload.defendingSide != null)
                payload.defendingSide?.let { TeamSide.STREAM_CODEC.encode(buf, it) }
                buf.writeBoolean(payload.penaltyKickerUuid != null)
                payload.penaltyKickerUuid?.let { buf.writeUUID(it) }
                buf.writeInt(payload.penaltyKickPhase?.ordinal ?: -1)
            },
            { buf ->
                val kind = SetPieceKind.STREAM_CODEC.decode(buf)
                val restartTeam = if (buf.readBoolean()) TeamSide.STREAM_CODEC.decode(buf) else null
                val goalKickPhase = if (buf.readBoolean()) GoalKickPhase.STREAM_CODEC.decode(buf) else null
                val goalKickPickerUuid = if (buf.readBoolean()) buf.readUUID() else null
                val throwInTakerUuid = if (buf.readBoolean()) buf.readUUID() else null
                val movementFrozen = buf.readBoolean()
                val ballPos = if (buf.readBoolean()) {
                    Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
                } else {
                    null
                }
                val defendingSide = if (buf.readBoolean()) TeamSide.STREAM_CODEC.decode(buf) else null
                val penaltyKickerUuid = if (buf.readBoolean()) buf.readUUID() else null
                val penaltyKickPhaseOrd = buf.readInt()
                val penaltyKickPhase = if (penaltyKickPhaseOrd < 0) {
                    null
                } else {
                    PenaltyKickPhase.entries[penaltyKickPhaseOrd]
                }
                SetPieceStateS2CPayload(
                    kind = kind,
                    restartTeam = restartTeam,
                    goalKickPhase = goalKickPhase,
                    goalKickPickerUuid = goalKickPickerUuid,
                    throwInTakerUuid = throwInTakerUuid,
                    movementFrozen = movementFrozen,
                    ballPos = ballPos,
                    defendingSide = defendingSide,
                    penaltyKickerUuid = penaltyKickerUuid,
                    penaltyKickPhase = penaltyKickPhase,
                )
            },
        )
    }
}
