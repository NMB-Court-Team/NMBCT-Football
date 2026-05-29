package net.astrorbits.football.config.server

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class ParticleBounceSettings(
    val bounceParticleMinGroundVy: Double = 0.12,
    val bounceParticleMinWallSpeed: Double = 0.08,
    val bounceParticleReferenceSpeed: Double = 0.45,
    val bounceParticleSpeed: Double = 0.04,
    val spreadHorizontal: Double = 0.22,
    val spreadVertical: Double = 0.12,
) {
    companion object {
        val CODEC: Codec<ParticleBounceSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.DOUBLE.fieldOf("bounce_particle_min_ground_vy").forGetter(ParticleBounceSettings::bounceParticleMinGroundVy),
                Codec.DOUBLE.fieldOf("bounce_particle_min_wall_speed").forGetter(ParticleBounceSettings::bounceParticleMinWallSpeed),
                Codec.DOUBLE.fieldOf("bounce_particle_reference_speed").forGetter(ParticleBounceSettings::bounceParticleReferenceSpeed),
                Codec.DOUBLE.fieldOf("bounce_particle_speed").forGetter(ParticleBounceSettings::bounceParticleSpeed),
                Codec.DOUBLE.fieldOf("spread_horizontal").forGetter(ParticleBounceSettings::spreadHorizontal),
                Codec.DOUBLE.fieldOf("spread_vertical").forGetter(ParticleBounceSettings::spreadVertical),
            ).apply(i, ::ParticleBounceSettings)
        }

        val DEFAULT = ParticleBounceSettings()
    }
}

data class ParticleActionCounts(
    val kickCountBase: Int = 8,
    val kickCountForceExtra: Int = 12,
    val dribbleCount: Int = 3,
    val trapCount: Int = 6,
    val placeCount: Int = 8,
    val gkCatchCount: Int = 6,
    val gkDiveCount: Int = 10,
    val gkPunchCount: Int = 8,
    val gkThrowCount: Int = 6,
    val kickParticleSpeed: Double = 0.06,
    val gkDiveParticleSpeed: Double = 0.03,
) {
    companion object {
        val CODEC: Codec<ParticleActionCounts> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.INT.fieldOf("kick_count_base").forGetter(ParticleActionCounts::kickCountBase),
                Codec.INT.fieldOf("kick_count_force_extra").forGetter(ParticleActionCounts::kickCountForceExtra),
                Codec.INT.fieldOf("dribble_count").forGetter(ParticleActionCounts::dribbleCount),
                Codec.INT.fieldOf("trap_count").forGetter(ParticleActionCounts::trapCount),
                Codec.INT.fieldOf("place_count").forGetter(ParticleActionCounts::placeCount),
                Codec.INT.fieldOf("gk_catch_count").forGetter(ParticleActionCounts::gkCatchCount),
                Codec.INT.fieldOf("gk_dive_count").forGetter(ParticleActionCounts::gkDiveCount),
                Codec.INT.fieldOf("gk_punch_count").forGetter(ParticleActionCounts::gkPunchCount),
                Codec.INT.fieldOf("gk_throw_count").forGetter(ParticleActionCounts::gkThrowCount),
                Codec.DOUBLE.fieldOf("kick_particle_speed").forGetter(ParticleActionCounts::kickParticleSpeed),
                Codec.DOUBLE.fieldOf("gk_dive_particle_speed").forGetter(ParticleActionCounts::gkDiveParticleSpeed),
            ).apply(i, ::ParticleActionCounts)
        }

        val DEFAULT = ParticleActionCounts()
    }
}

data class ParticleHighSpeedDragSettings(
    val highSpeedDragMinSpeed: Double = 0.42,
    val highSpeedDragCountBase: Int = 4,
    val highSpeedDragCountExtra: Int = 12,
    val highSpeedDragReferenceSpeed: Double = 0.85,
    val highSpeedDragRingRadius: Double = 0.34,
    val highSpeedDragTrailForwardDistance: Double = 0.50,
    val highSpeedDragVerticalOffset: Double = 0.2,
    val highSpeedDragTrailDurationTicks: Int = 10,
    val highSpeedDragTrailColorLowRgb: Int = 0x6FB8FF,
    val highSpeedDragTrailColorHighRgb: Int = 0xFF4E4E,
    val highSpeedDragColorRedStart: Float = 0.60f,
) {
    companion object {
        val CODEC: Codec<ParticleHighSpeedDragSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.DOUBLE.fieldOf("high_speed_drag_min_speed").forGetter(ParticleHighSpeedDragSettings::highSpeedDragMinSpeed),
                Codec.INT.fieldOf("high_speed_drag_count_base").forGetter(ParticleHighSpeedDragSettings::highSpeedDragCountBase),
                Codec.INT.fieldOf("high_speed_drag_count_extra").forGetter(ParticleHighSpeedDragSettings::highSpeedDragCountExtra),
                Codec.DOUBLE.fieldOf("high_speed_drag_reference_speed").forGetter(ParticleHighSpeedDragSettings::highSpeedDragReferenceSpeed),
                Codec.DOUBLE.fieldOf("high_speed_drag_ring_radius").forGetter(ParticleHighSpeedDragSettings::highSpeedDragRingRadius),
                Codec.DOUBLE.fieldOf("high_speed_drag_trail_forward_distance").forGetter(ParticleHighSpeedDragSettings::highSpeedDragTrailForwardDistance),
                Codec.DOUBLE.fieldOf("high_speed_drag_vertical_offset").forGetter(ParticleHighSpeedDragSettings::highSpeedDragVerticalOffset),
                Codec.INT.fieldOf("high_speed_drag_trail_duration_ticks").forGetter(ParticleHighSpeedDragSettings::highSpeedDragTrailDurationTicks),
                Codec.INT.fieldOf("high_speed_drag_trail_color_low_rgb").forGetter(ParticleHighSpeedDragSettings::highSpeedDragTrailColorLowRgb),
                Codec.INT.fieldOf("high_speed_drag_trail_color_high_rgb").forGetter(ParticleHighSpeedDragSettings::highSpeedDragTrailColorHighRgb),
                Codec.FLOAT.fieldOf("high_speed_drag_color_red_start").forGetter(ParticleHighSpeedDragSettings::highSpeedDragColorRedStart),
            ).apply(i, ::ParticleHighSpeedDragSettings)
        }

        val DEFAULT = ParticleHighSpeedDragSettings()
    }
}

data class ParticleKickVisualSettings(
    val kickSweepFootForward: Double = 0.15,
    val kickSweepFootHeight: Double = 0.10,
    val kickCloudRingBaseCount: Int = 8,
    val kickCloudRingForceExtra: Int = 8,
    val kickCloudRingInnerRadius: Double = 0.68,
    val kickCloudRingOuterRadius: Double = 1.00,
    val kickCloudRingRadialSpeed: Double = 0.035,
) {
    companion object {
        val CODEC: Codec<ParticleKickVisualSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.DOUBLE.fieldOf("kick_sweep_foot_forward").forGetter(ParticleKickVisualSettings::kickSweepFootForward),
                Codec.DOUBLE.fieldOf("kick_sweep_foot_height").forGetter(ParticleKickVisualSettings::kickSweepFootHeight),
                Codec.INT.fieldOf("kick_cloud_ring_base_count").forGetter(ParticleKickVisualSettings::kickCloudRingBaseCount),
                Codec.INT.fieldOf("kick_cloud_ring_force_extra").forGetter(ParticleKickVisualSettings::kickCloudRingForceExtra),
                Codec.DOUBLE.fieldOf("kick_cloud_ring_inner_radius").forGetter(ParticleKickVisualSettings::kickCloudRingInnerRadius),
                Codec.DOUBLE.fieldOf("kick_cloud_ring_outer_radius").forGetter(ParticleKickVisualSettings::kickCloudRingOuterRadius),
                Codec.DOUBLE.fieldOf("kick_cloud_ring_radial_speed").forGetter(ParticleKickVisualSettings::kickCloudRingRadialSpeed),
            ).apply(i, ::ParticleKickVisualSettings)
        }

        val DEFAULT = ParticleKickVisualSettings()
    }
}

/** 足球粒子效果（服务端生成）。 */
data class ParticleSettings(
    val bounce: ParticleBounceSettings = ParticleBounceSettings.DEFAULT,
    val counts: ParticleActionCounts = ParticleActionCounts.DEFAULT,
    val highSpeedDrag: ParticleHighSpeedDragSettings = ParticleHighSpeedDragSettings.DEFAULT,
    val kickVisual: ParticleKickVisualSettings = ParticleKickVisualSettings.DEFAULT,
) {
    val bounceParticleMinGroundVy get() = bounce.bounceParticleMinGroundVy
    val bounceParticleMinWallSpeed get() = bounce.bounceParticleMinWallSpeed
    val bounceParticleReferenceSpeed get() = bounce.bounceParticleReferenceSpeed
    val bounceParticleSpeed get() = bounce.bounceParticleSpeed
    val spreadHorizontal get() = bounce.spreadHorizontal
    val spreadVertical get() = bounce.spreadVertical
    val kickCountBase get() = counts.kickCountBase
    val kickCountForceExtra get() = counts.kickCountForceExtra
    val dribbleCount get() = counts.dribbleCount
    val trapCount get() = counts.trapCount
    val placeCount get() = counts.placeCount
    val gkCatchCount get() = counts.gkCatchCount
    val gkDiveCount get() = counts.gkDiveCount
    val gkPunchCount get() = counts.gkPunchCount
    val gkThrowCount get() = counts.gkThrowCount
    val kickParticleSpeed get() = counts.kickParticleSpeed
    val gkDiveParticleSpeed get() = counts.gkDiveParticleSpeed
    val highSpeedDragMinSpeed get() = highSpeedDrag.highSpeedDragMinSpeed
    val highSpeedDragCountBase get() = highSpeedDrag.highSpeedDragCountBase
    val highSpeedDragCountExtra get() = highSpeedDrag.highSpeedDragCountExtra
    val highSpeedDragReferenceSpeed get() = highSpeedDrag.highSpeedDragReferenceSpeed
    val highSpeedDragRingRadius get() = highSpeedDrag.highSpeedDragRingRadius
    val highSpeedDragTrailForwardDistance get() = highSpeedDrag.highSpeedDragTrailForwardDistance
    val highSpeedDragVerticalOffset get() = highSpeedDrag.highSpeedDragVerticalOffset
    val highSpeedDragTrailDurationTicks get() = highSpeedDrag.highSpeedDragTrailDurationTicks
    val highSpeedDragTrailColorLowRgb get() = highSpeedDrag.highSpeedDragTrailColorLowRgb
    val highSpeedDragTrailColorHighRgb get() = highSpeedDrag.highSpeedDragTrailColorHighRgb
    val highSpeedDragColorRedStart get() = highSpeedDrag.highSpeedDragColorRedStart
    val kickSweepFootForward get() = kickVisual.kickSweepFootForward
    val kickSweepFootHeight get() = kickVisual.kickSweepFootHeight
    val kickCloudRingBaseCount get() = kickVisual.kickCloudRingBaseCount
    val kickCloudRingForceExtra get() = kickVisual.kickCloudRingForceExtra
    val kickCloudRingInnerRadius get() = kickVisual.kickCloudRingInnerRadius
    val kickCloudRingOuterRadius get() = kickVisual.kickCloudRingOuterRadius
    val kickCloudRingRadialSpeed get() = kickVisual.kickCloudRingRadialSpeed

    companion object {
        val CODEC: Codec<ParticleSettings> = RecordCodecBuilder.create { i ->
            i.group(
                ParticleBounceSettings.CODEC.fieldOf("bounce").forGetter(ParticleSettings::bounce),
                ParticleActionCounts.CODEC.fieldOf("counts").forGetter(ParticleSettings::counts),
                ParticleHighSpeedDragSettings.CODEC.fieldOf("high_speed_drag").forGetter(ParticleSettings::highSpeedDrag),
                ParticleKickVisualSettings.CODEC.fieldOf("kick_visual").forGetter(ParticleSettings::kickVisual),
            ).apply(i, ::ParticleSettings)
        }

        val DEFAULT = ParticleSettings()
    }
}
