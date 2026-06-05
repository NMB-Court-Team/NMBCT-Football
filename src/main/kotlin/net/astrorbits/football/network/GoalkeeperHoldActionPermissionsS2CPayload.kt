package net.astrorbits.football.network

import net.astrorbits.football.NMBCTFootball
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** 同步球员捡球 / 放下 / 抛出权限（默认均为 true）。 */
data class GoalkeeperHoldActionPermissionsS2CPayload(
    val canCatch: Boolean,
    val canDrop: Boolean,
    val canThrow: Boolean,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GoalkeeperHoldActionPermissionsS2CPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<GoalkeeperHoldActionPermissionsS2CPayload>(
            NMBCTFootball.id("goalkeeper_hold_action_permissions"),
        )

        val CODEC: StreamCodec<FriendlyByteBuf, GoalkeeperHoldActionPermissionsS2CPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeBoolean(payload.canCatch)
                buf.writeBoolean(payload.canDrop)
                buf.writeBoolean(payload.canThrow)
            },
            { buf ->
                GoalkeeperHoldActionPermissionsS2CPayload(
                    canCatch = buf.readBoolean(),
                    canDrop = buf.readBoolean(),
                    canThrow = buf.readBoolean(),
                )
            },
        )
    }
}
