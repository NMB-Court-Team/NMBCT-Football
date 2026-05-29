package net.astrorbits.football.config.server

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class PlayerChipSettings(
    val chipForce: Double = 1.4,
    val chipAngleDeg: Double = 50.0,
    val chipAngleExtraMax: Double = 20.0,
    val chipHeightOffset: Double = -0.15,
) {
    companion object {
        val CODEC: Codec<PlayerChipSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.DOUBLE.fieldOf("chip_force").forGetter(PlayerChipSettings::chipForce),
                Codec.DOUBLE.fieldOf("chip_angle_deg").forGetter(PlayerChipSettings::chipAngleDeg),
                Codec.DOUBLE.fieldOf("chip_angle_extra_max").forGetter(PlayerChipSettings::chipAngleExtraMax),
                Codec.DOUBLE.fieldOf("chip_height_offset").forGetter(PlayerChipSettings::chipHeightOffset),
            ).apply(i, ::PlayerChipSettings)
        }

        val DEFAULT = PlayerChipSettings()
    }
}

data class PlayerKickSettings(
    val playerKickRange: Double = 2.5,
    val commandKickRange: Double = 3.0,
    val passForce: Double = 1.5,
    val shootForceMin: Double = 2.0,
    val shootForceMax: Double = 4.0,
    val shootSprintBonus: Double = 1.15,
    val shootMinChargeForSprint: Float = 0.6f,
    val actionCooldownTicks: Int = 3,
    val shootAngleMinDeg: Double = 5.0,
    val shootAngleMaxDeg: Double = 18.0,
    val kickLookPitchInfluence: Double = 0.35,
    val kickLookPitchAngleMin: Double = -10.0,
    val kickLookPitchAngleMax: Double = 15.0,
    val chip: PlayerChipSettings = PlayerChipSettings.DEFAULT,
) {
    val chipForce get() = chip.chipForce
    val chipAngleDeg get() = chip.chipAngleDeg
    val chipAngleExtraMax get() = chip.chipAngleExtraMax
    val chipHeightOffset get() = chip.chipHeightOffset

    companion object {
        val CODEC: Codec<PlayerKickSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.DOUBLE.fieldOf("player_kick_range").forGetter(PlayerKickSettings::playerKickRange),
                Codec.DOUBLE.fieldOf("command_kick_range").forGetter(PlayerKickSettings::commandKickRange),
                Codec.DOUBLE.fieldOf("pass_force").forGetter(PlayerKickSettings::passForce),
                Codec.DOUBLE.fieldOf("shoot_force_min").forGetter(PlayerKickSettings::shootForceMin),
                Codec.DOUBLE.fieldOf("shoot_force_max").forGetter(PlayerKickSettings::shootForceMax),
                Codec.DOUBLE.fieldOf("shoot_sprint_bonus").forGetter(PlayerKickSettings::shootSprintBonus),
                Codec.FLOAT.fieldOf("shoot_min_charge_for_sprint").forGetter(PlayerKickSettings::shootMinChargeForSprint),
                Codec.INT.fieldOf("action_cooldown_ticks").forGetter(PlayerKickSettings::actionCooldownTicks),
                Codec.DOUBLE.fieldOf("shoot_angle_min_deg").forGetter(PlayerKickSettings::shootAngleMinDeg),
                Codec.DOUBLE.fieldOf("shoot_angle_max_deg").forGetter(PlayerKickSettings::shootAngleMaxDeg),
                Codec.DOUBLE.fieldOf("kick_look_pitch_influence").forGetter(PlayerKickSettings::kickLookPitchInfluence),
                Codec.DOUBLE.fieldOf("kick_look_pitch_angle_min").forGetter(PlayerKickSettings::kickLookPitchAngleMin),
                Codec.DOUBLE.fieldOf("kick_look_pitch_angle_max").forGetter(PlayerKickSettings::kickLookPitchAngleMax),
                PlayerChipSettings.CODEC.fieldOf("chip").forGetter(PlayerKickSettings::chip),
            ).apply(i, ::PlayerKickSettings)
        }

        val DEFAULT = PlayerKickSettings()
    }
}

data class PlayerDribbleSettings(
    val dribbleTargetDistance: Double = 0.9,
    val dribbleMaxControlRange: Double = 2.5,
    val dribbleSessionTimeoutTicks: Int = 6,
    val dribbleSoundIntervalTicks: Int = 6,
    val dribbleSoundIntervalJitterTicks: Int = 2,
    val dribblePositionGain: Double = 0.35,
    val dribbleVelocityGain: Double = 0.55,
    val dribbleMaxCorrection: Double = 0.12,
    val dribbleSpeedMatch: Double = 0.85,
    val dribbleLateralGain: Double = 0.5,
    val dribbleTouchForce: Double = 0.08,
    val dribbleAirPositionScale: Double = 0.25,
) {
    companion object {
        val CODEC: Codec<PlayerDribbleSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.DOUBLE.fieldOf("dribble_target_distance").forGetter(PlayerDribbleSettings::dribbleTargetDistance),
                Codec.DOUBLE.fieldOf("dribble_max_control_range").forGetter(PlayerDribbleSettings::dribbleMaxControlRange),
                Codec.INT.fieldOf("dribble_session_timeout_ticks").forGetter(PlayerDribbleSettings::dribbleSessionTimeoutTicks),
                Codec.INT.fieldOf("dribble_sound_interval_ticks").forGetter(PlayerDribbleSettings::dribbleSoundIntervalTicks),
                Codec.INT.fieldOf("dribble_sound_interval_jitter_ticks").forGetter(PlayerDribbleSettings::dribbleSoundIntervalJitterTicks),
                Codec.DOUBLE.fieldOf("dribble_position_gain").forGetter(PlayerDribbleSettings::dribblePositionGain),
                Codec.DOUBLE.fieldOf("dribble_velocity_gain").forGetter(PlayerDribbleSettings::dribbleVelocityGain),
                Codec.DOUBLE.fieldOf("dribble_max_correction").forGetter(PlayerDribbleSettings::dribbleMaxCorrection),
                Codec.DOUBLE.fieldOf("dribble_speed_match").forGetter(PlayerDribbleSettings::dribbleSpeedMatch),
                Codec.DOUBLE.fieldOf("dribble_lateral_gain").forGetter(PlayerDribbleSettings::dribbleLateralGain),
                Codec.DOUBLE.fieldOf("dribble_touch_force").forGetter(PlayerDribbleSettings::dribbleTouchForce),
                Codec.DOUBLE.fieldOf("dribble_air_position_scale").forGetter(PlayerDribbleSettings::dribbleAirPositionScale),
            ).apply(i, ::PlayerDribbleSettings)
        }

        val DEFAULT = PlayerDribbleSettings()
    }
}

/** 球员操作、踢球力度与运球辅助（服务端权威）。 */
data class PlayerInputSettings(
    val kick: PlayerKickSettings = PlayerKickSettings.DEFAULT,
    val dribble: PlayerDribbleSettings = PlayerDribbleSettings.DEFAULT,
    val charge: KickChargeSettings = KickChargeSettings.DEFAULT,
) {
    val playerKickRange get() = kick.playerKickRange
    val commandKickRange get() = kick.commandKickRange
    val passForce get() = kick.passForce
    val shootForceMin get() = kick.shootForceMin
    val shootForceMax get() = kick.shootForceMax
    val shootSprintBonus get() = kick.shootSprintBonus
    val shootMinChargeForSprint get() = kick.shootMinChargeForSprint
    val chipForce get() = kick.chipForce
    val chipAngleDeg get() = kick.chipAngleDeg
    val chipAngleExtraMax get() = kick.chipAngleExtraMax
    val chipHeightOffset get() = kick.chipHeightOffset
    val actionCooldownTicks get() = kick.actionCooldownTicks
    val shootAngleMinDeg get() = kick.shootAngleMinDeg
    val shootAngleMaxDeg get() = kick.shootAngleMaxDeg
    val kickLookPitchInfluence get() = kick.kickLookPitchInfluence
    val kickLookPitchAngleMin get() = kick.kickLookPitchAngleMin
    val kickLookPitchAngleMax get() = kick.kickLookPitchAngleMax
    val dribbleTargetDistance get() = dribble.dribbleTargetDistance
    val dribbleMaxControlRange get() = dribble.dribbleMaxControlRange
    val dribbleSessionTimeoutTicks get() = dribble.dribbleSessionTimeoutTicks
    val dribbleSoundIntervalTicks get() = dribble.dribbleSoundIntervalTicks
    val dribbleSoundIntervalJitterTicks get() = dribble.dribbleSoundIntervalJitterTicks
    val dribblePositionGain get() = dribble.dribblePositionGain
    val dribbleVelocityGain get() = dribble.dribbleVelocityGain
    val dribbleMaxCorrection get() = dribble.dribbleMaxCorrection
    val dribbleSpeedMatch get() = dribble.dribbleSpeedMatch
    val dribbleLateralGain get() = dribble.dribbleLateralGain
    val dribbleTouchForce get() = dribble.dribbleTouchForce
    val dribbleAirPositionScale get() = dribble.dribbleAirPositionScale
    val tapMaxMs get() = charge.tapMaxMs
    val chargeMinMs get() = charge.chargeMinMs
    val chargeRiseMs get() = charge.chargeRiseMs
    val chargePerfectWindowMs get() = charge.chargePerfectWindowMs
    val chargeDecayMs get() = charge.chargeDecayMs
    val kickSpreadInaccuracy get() = charge.kickSpreadInaccuracy
    val perfectChargeForceBonus get() = charge.perfectChargeForceBonus

    companion object {
        val CODEC: Codec<PlayerInputSettings> = RecordCodecBuilder.create { i ->
            i.group(
                PlayerKickSettings.CODEC.fieldOf("kick").forGetter(PlayerInputSettings::kick),
                PlayerDribbleSettings.CODEC.fieldOf("dribble").forGetter(PlayerInputSettings::dribble),
                KickChargeSettings.CODEC.optionalFieldOf("charge", KickChargeSettings.DEFAULT)
                    .forGetter(PlayerInputSettings::charge),
            ).apply(i, ::PlayerInputSettings)
        }

        val DEFAULT = PlayerInputSettings()
    }
}
