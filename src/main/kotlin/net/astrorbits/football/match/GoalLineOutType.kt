package net.astrorbits.football.match

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

enum class GoalLineOutType {
    CORNER_KICK,
    GOAL_KICK,
    THROW_IN;

    companion object {
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, GoalLineOutType> = StreamCodec.of(
            { buf, v -> buf.writeEnum(v) },
            { buf -> buf.readEnum(GoalLineOutType::class.java) },
        )
    }
}
