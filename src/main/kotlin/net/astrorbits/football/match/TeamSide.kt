package net.astrorbits.football.match

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

enum class TeamSide {
    A,
    B;

    companion object {
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, TeamSide> = StreamCodec.of(
            { buf, v -> buf.writeEnum(v) },
            { buf -> buf.readEnum(TeamSide::class.java) },
        )
    }
}
