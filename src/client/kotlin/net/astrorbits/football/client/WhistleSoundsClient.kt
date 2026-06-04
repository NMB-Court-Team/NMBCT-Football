package net.astrorbits.football.client

import net.astrorbits.football.FootballSounds
import net.astrorbits.football.network.WhistleUseS2CPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance
import net.minecraft.world.entity.Entity

/** 哨子吹哨：绑定实体播放，音源随玩家移动（与 [SlideTackleSoundsClient] 一致）。 */
object WhistleSoundsClient {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(WhistleUseS2CPayload.TYPE) { payload, _ ->
            val level = Minecraft.getInstance().level ?: return@registerGlobalReceiver
            val entity = level.getEntity(payload.entityId) ?: return@registerGlobalReceiver
            playBound(entity, FootballSounds.WHISTLE_USE)
        }
    }

    private fun playBound(entity: Entity, spec: FootballSounds.SoundSpec) {
        val pitch = spec.resolvePitch(entity.random).coerceAtMost(2.0f)
        Minecraft.getInstance().soundManager.play(
            EntityBoundSoundInstance(
                spec.event,
                spec.source,
                spec.volume,
                pitch,
                entity,
                entity.random.nextLong(),
            ),
        )
    }
}
