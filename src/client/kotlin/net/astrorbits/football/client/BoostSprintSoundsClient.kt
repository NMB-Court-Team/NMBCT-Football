package net.astrorbits.football.client

import net.astrorbits.football.FootballSounds
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent

/** 加速疾跑进入/退出音效：仅本地玩家可听见。 */
object BoostSprintSoundsClient {
    fun playStart() {
        play(FootballSounds.boostSprintStartEvent, 0.9f, 1.15f)
    }

    fun playEnd() {
        play(FootballSounds.boostSprintEndEvent, 0.85f, 0.92f)
    }

    private fun play(event: SoundEvent, volume: Float, pitch: Float) {
        val client = Minecraft.getInstance()
        if (client.player == null) {
            return
        }
        client.soundManager.play(
            SimpleSoundInstance.forLocalAmbience(event, volume, pitch),
        )
    }
}
