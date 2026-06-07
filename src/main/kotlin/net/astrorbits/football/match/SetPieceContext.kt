package net.astrorbits.football.match

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3
import java.util.*

enum class SetPieceKind {
    NONE,
    CENTER_KICKOFF,
    GOAL_KICK,
    CORNER_KICK,
    THROW_IN,
    PENALTY_KICK,
    ;

    companion object {
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, SetPieceKind> = StreamCodec.of(
            { buf, v -> buf.writeEnum(v) },
            { buf -> buf.readEnum(SetPieceKind::class.java) },
        )
    }
}

enum class GoalKickPhase {
    WAITING_PICKUP,
    PLACING,
    PLACED,
    ;

    companion object {
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, GoalKickPhase> = StreamCodec.of(
            { buf, v -> buf.writeEnum(v) },
            { buf -> buf.readEnum(GoalKickPhase::class.java) },
        )
    }
}

enum class SetPieceRestartKind {
    KICKOFF,
    GOAL_KICK,
    CORNER_KICK,
    THROW_IN,
    ;

    companion object {
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, SetPieceRestartKind> = StreamCodec.of(
            { buf, v -> buf.writeEnum(v) },
            { buf -> buf.readEnum(SetPieceRestartKind::class.java) },
        )
    }
}

data class SetPieceContext(
    val kind: SetPieceKind,
    val restartTeam: TeamSide,
    val ballPos: Vec3,
    val defendingSide: TeamSide? = null,
    val throwInTakerUuid: UUID? = null,
    val goalKickPhase: GoalKickPhase? = null,
    val goalKickPickerUuid: UUID? = null,
    val cornerPos: Vec3? = null,
    val cornerKickTakerUuid: UUID? = null,
)

object SetPieceState {
    var active: SetPieceContext? = null
        private set

    fun begin(context: SetPieceContext) {
        active = context
    }

    fun update(transform: (SetPieceContext) -> SetPieceContext) {
        val current = active ?: return
        active = transform(current)
    }

    fun clear() {
        active = null
    }

    fun isActive(): Boolean = active != null

    fun kind(): SetPieceKind = active?.kind ?: SetPieceKind.NONE
}
