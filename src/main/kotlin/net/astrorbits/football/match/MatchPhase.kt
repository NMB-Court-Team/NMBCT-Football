package net.astrorbits.football.match

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

enum class MatchPhase(val displayNameKey: String) {
    PRE_MATCH("match.phase.pre_match"),
    FIRST_HALF("match.phase.first_half"),
    FIRST_HALF_ET("match.phase.first_half_et"),
    SECOND_HALF("match.phase.second_half"),
    SECOND_HALF_ET("match.phase.second_half_et"),
    EXTRA_FIRST("match.phase.extra_first"),
    EXTRA_FIRST_ET("match.phase.extra_first_et"),
    EXTRA_SECOND("match.phase.extra_second"),
    EXTRA_SECOND_ET("match.phase.extra_second_et"),
    PENALTIES("match.phase.penalties"),
    FINISHED("match.phase.finished");

    companion object {
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, MatchPhase> =
            StreamCodec.of(
                { buf, phase -> buf.writeInt(phase.ordinal) },
                { buf -> entries[buf.readInt()] },
            )
    }

    /** 下一阶段。FINISHED 没有下一阶段，返回 null。 */
    val next: MatchPhase?
        get() = when (this) {
            PRE_MATCH -> FIRST_HALF
            FIRST_HALF -> FIRST_HALF_ET
            FIRST_HALF_ET -> SECOND_HALF
            SECOND_HALF -> SECOND_HALF_ET
            SECOND_HALF_ET -> EXTRA_FIRST
            EXTRA_FIRST -> EXTRA_FIRST_ET
            EXTRA_FIRST_ET -> EXTRA_SECOND
            EXTRA_SECOND -> EXTRA_SECOND_ET
            EXTRA_SECOND_ET -> PENALTIES
            PENALTIES -> FINISHED
            FINISHED -> null
        }
}
