package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class GoalkeeperRoleS2CPayload(
    val isGoalkeeper: Boolean,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GoalkeeperRoleS2CPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<GoalkeeperRoleS2CPayload>(NMBCTFootball.id("goalkeeper_role"))

        val CODEC: StreamCodec<FriendlyByteBuf, GoalkeeperRoleS2CPayload> = StreamCodec.of(
            { buf, payload -> buf.writeBoolean(payload.isGoalkeeper) },
            { buf -> GoalkeeperRoleS2CPayload(isGoalkeeper = buf.readBoolean()) },
        )
    }
}
