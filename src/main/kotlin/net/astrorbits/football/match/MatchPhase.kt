package net.astrorbits.football.match

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

enum class MatchPhase(val displayNameKey: String) {
    PRE_MATCH("match.phase.pre_match"), // 未开始
    FIRST_HALF("match.phase.first_half"), // 上半场
    FIRST_HALF_ET("match.phase.first_half_et"), // 上半场补时
    SECOND_HALF("match.phase.second_half"), // 下半场
    SECOND_HALF_ET("match.phase.second_half_et"), // 下半场补时
    EXTRA_FIRST("match.phase.extra_first"), // 加时上半场
    EXTRA_FIRST_ET("match.phase.extra_first_et"), // 加时上半场补时
    EXTRA_SECOND("match.phase.extra_second"), // 加时下半场
    EXTRA_SECOND_ET("match.phase.extra_second_et"), // 加时下半场补时
    PENALTIES("match.phase.penalties"), // 点球大战
    PRE_MATCH_PREP("match.phase.pre_match_prep"), // 赛前准备
    FINISHED("match.phase.finished"); // 结算

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
            PRE_MATCH_PREP -> FIRST_HALF
            FINISHED -> null
        }
}
