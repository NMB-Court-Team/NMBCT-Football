package net.astrorbits.football.client

import net.astrorbits.football.FootballSounds
import net.astrorbits.football.config.FootballConfigs
import net.astrorbits.football.config.server.PlayerSlideTackleSettings
import net.astrorbits.football.input.SlideTackleSoundTiming
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance
import net.minecraft.world.entity.Entity
import kotlin.math.sqrt

/** 滑铲音效：绑定实体播放，附近玩家均可听见。 */
object SlideTackleSoundsClient {
    private data class SoundSession(
        val entityId: Int,
        val startTick: Long,
        var endSoundPlayed: Boolean = false,
        var contactSpeedScale: Double = 1.0,
    )

    private val sessions = mutableMapOf<Int, SoundSession>()

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    fun onSlideStart(entity: Entity, startTick: Long) {
        sessions[entity.id] = SoundSession(entityId = entity.id, startTick = startTick)
        playBound(entity, FootballSounds.SLIDE_TACKLE_START)
    }

    fun onSlideEnd(entity: Entity) {
        val session = sessions.remove(entity.id) ?: return
        if (!session.endSoundPlayed) {
            playBound(entity, FootballSounds.SLIDE_TACKLE_END)
        }
    }

    fun clear() {
        sessions.clear()
    }

    private fun tick(client: Minecraft) {
        val level = client.level ?: run {
            clear()
            return
        }
        if (sessions.isEmpty()) {
            return
        }

        val settings = FootballConfigs.server.playerInput.slide
        val now = level.gameTime
        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            val (_, session) = iterator.next()
            val entity = level.getEntity(session.entityId)
            if (entity == null || !entity.isAlive) {
                iterator.remove()
                continue
            }

            updateContactSpeedScale(entity, session, settings, now)
            val elapsed = now - session.startTick

            if (!session.endSoundPlayed &&
                SlideTackleSoundTiming.shouldPlayEndEarly(settings, elapsed, session.contactSpeedScale)
            ) {
                session.endSoundPlayed = true
                playBound(entity, FootballSounds.SLIDE_TACKLE_END)
            }

            if (SlideTackleSoundTiming.shouldPlayLoop(
                    elapsed,
                    session.endSoundPlayed,
                    settings,
                    session.contactSpeedScale,
                )
            ) {
                playBound(entity, FootballSounds.SLIDE_TACKLE_LOOP)
            }
        }
    }

    private fun updateContactSpeedScale(
        entity: Entity,
        session: SoundSession,
        settings: PlayerSlideTackleSettings,
        now: Long,
    ) {
        val elapsed = now - session.startTick
        val expected = SlideTackleSoundTiming.slideSpeedAtTick(settings, elapsed, 1.0)
        if (expected <= SlideTackleSoundTiming.MOVE_EPSILON) {
            return
        }
        val horizontalSpeed = sqrt(entity.deltaMovement.x * entity.deltaMovement.x + entity.deltaMovement.z * entity.deltaMovement.z)
        val scale = (horizontalSpeed / expected).coerceIn(0.0, 1.0)
        if (scale < session.contactSpeedScale) {
            session.contactSpeedScale = scale
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
