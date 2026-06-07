package net.astrorbits.football.match

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

enum class FreeKickFoulReason {
    OFFSIDE,
    GOALKEEPER_LEFT_PENALTY_AREA,
    SLIDE_TACKLE_IN_PENALTY_AREA,
    SECOND_TOUCH,
    ;

    companion object {
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, FreeKickFoulReason> = StreamCodec.of(
            { buf, v -> buf.writeEnum(v) },
            { buf -> buf.readEnum(FreeKickFoulReason::class.java) },
        )
    }
}

enum class FreeKickType {
    DIRECT,
    INDIRECT,
    PENALTY,
    ;

    companion object {
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, FreeKickType> = StreamCodec.of(
            { buf, v -> buf.writeEnum(v) },
            { buf -> buf.readEnum(FreeKickType::class.java) },
        )
    }
}
