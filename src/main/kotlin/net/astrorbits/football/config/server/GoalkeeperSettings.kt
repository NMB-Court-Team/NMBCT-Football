package net.astrorbits.football.config.server

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class GoalkeeperCatchSettings(
    val catchRange: Double = 3.5,
    val crouchRangeBonus: Double = 0.3,
    val catchMaxSpeed: Double = 1.8,
    val catchAngleDeg: Double = 120.0,
    val holdMaxTicks: Int = 300,
    val holdHeight: Double = 1.05,
    val holdForward: Double = 0.85,
    val holdCrouchHeightOffset: Double = 0.15,
    val holdFirstPersonExtraForward: Double = 0.15,
    val holdFirstPersonExtraDown: Double = 0.12,
    val dropDistance: Double = 0.5,
    val holdReleaseLockTicks: Int = 25,
    val actionCooldownTicks: Int = 3,
) {
    companion object {
        val CODEC: Codec<GoalkeeperCatchSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.DOUBLE.fieldOf("catch_range").forGetter(GoalkeeperCatchSettings::catchRange),
                Codec.DOUBLE.fieldOf("crouch_range_bonus").forGetter(GoalkeeperCatchSettings::crouchRangeBonus),
                Codec.DOUBLE.fieldOf("catch_max_speed").forGetter(GoalkeeperCatchSettings::catchMaxSpeed),
                Codec.DOUBLE.fieldOf("catch_angle_deg").forGetter(GoalkeeperCatchSettings::catchAngleDeg),
                Codec.INT.fieldOf("hold_max_ticks").forGetter(GoalkeeperCatchSettings::holdMaxTicks),
                Codec.DOUBLE.fieldOf("hold_height").forGetter(GoalkeeperCatchSettings::holdHeight),
                Codec.DOUBLE.fieldOf("hold_forward").forGetter(GoalkeeperCatchSettings::holdForward),
                Codec.DOUBLE.fieldOf("hold_crouch_height_offset").forGetter(GoalkeeperCatchSettings::holdCrouchHeightOffset),
                Codec.DOUBLE.fieldOf("hold_first_person_extra_forward").forGetter(GoalkeeperCatchSettings::holdFirstPersonExtraForward),
                Codec.DOUBLE.fieldOf("hold_first_person_extra_down").forGetter(GoalkeeperCatchSettings::holdFirstPersonExtraDown),
                Codec.DOUBLE.fieldOf("drop_distance").forGetter(GoalkeeperCatchSettings::dropDistance),
                Codec.INT.fieldOf("hold_release_lock_ticks").forGetter(GoalkeeperCatchSettings::holdReleaseLockTicks),
                Codec.INT.fieldOf("action_cooldown_ticks").forGetter(GoalkeeperCatchSettings::actionCooldownTicks),
            ).apply(i, ::GoalkeeperCatchSettings)
        }

        val DEFAULT = GoalkeeperCatchSettings()
    }
}

data class GoalkeeperDiveSettings(
    val diveDurationTicks: Int = 8,
    val diveCooldownTicks: Int = 24,
    val diveRange: Double = 4.0,
    val diveHalfAngleDeg: Double = 45.0,
    val diveSpeed: Double = 0.35,
    val diveCatchMaxSpeed: Double = 2.2,
    val diveDeflectForceScale: Double = 0.6,
    val punchRange: Double = 3.0,
    val punchForce: Double = 1.2,
    val throwShortForce: Double = 1.2,
    val throwLongForceMin: Double = 1.8,
    val throwLongForceMax: Double = 3.2,
    val throwLongAngleMinDeg: Double = 5.0,
    val throwLongAngleMaxDeg: Double = 12.0,
    val throwSprintBonus: Double = 1.1,
) {
    companion object {
        val CODEC: Codec<GoalkeeperDiveSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.INT.fieldOf("dive_duration_ticks").forGetter(GoalkeeperDiveSettings::diveDurationTicks),
                Codec.INT.fieldOf("dive_cooldown_ticks").forGetter(GoalkeeperDiveSettings::diveCooldownTicks),
                Codec.DOUBLE.fieldOf("dive_range").forGetter(GoalkeeperDiveSettings::diveRange),
                Codec.DOUBLE.fieldOf("dive_half_angle_deg").forGetter(GoalkeeperDiveSettings::diveHalfAngleDeg),
                Codec.DOUBLE.fieldOf("dive_speed").forGetter(GoalkeeperDiveSettings::diveSpeed),
                Codec.DOUBLE.fieldOf("dive_catch_max_speed").forGetter(GoalkeeperDiveSettings::diveCatchMaxSpeed),
                Codec.DOUBLE.fieldOf("dive_deflect_force_scale").forGetter(GoalkeeperDiveSettings::diveDeflectForceScale),
                Codec.DOUBLE.fieldOf("punch_range").forGetter(GoalkeeperDiveSettings::punchRange),
                Codec.DOUBLE.fieldOf("punch_force").forGetter(GoalkeeperDiveSettings::punchForce),
                Codec.DOUBLE.fieldOf("throw_short_force").forGetter(GoalkeeperDiveSettings::throwShortForce),
                Codec.DOUBLE.fieldOf("throw_long_force_min").forGetter(GoalkeeperDiveSettings::throwLongForceMin),
                Codec.DOUBLE.fieldOf("throw_long_force_max").forGetter(GoalkeeperDiveSettings::throwLongForceMax),
                Codec.DOUBLE.fieldOf("throw_long_angle_min_deg").forGetter(GoalkeeperDiveSettings::throwLongAngleMinDeg),
                Codec.DOUBLE.fieldOf("throw_long_angle_max_deg").forGetter(GoalkeeperDiveSettings::throwLongAngleMaxDeg),
                Codec.DOUBLE.fieldOf("throw_sprint_bonus").forGetter(GoalkeeperDiveSettings::throwSprintBonus),
            ).apply(i, ::GoalkeeperDiveSettings)
        }

        val DEFAULT = GoalkeeperDiveSettings()
    }
}

/** 守门员操作与持球（服务端权威）。 */
data class GoalkeeperSettings(
    val catch: GoalkeeperCatchSettings = GoalkeeperCatchSettings.DEFAULT,
    val dive: GoalkeeperDiveSettings = GoalkeeperDiveSettings.DEFAULT,
) {
    val catchRange get() = catch.catchRange
    val crouchRangeBonus get() = catch.crouchRangeBonus
    val catchMaxSpeed get() = catch.catchMaxSpeed
    val catchAngleDeg get() = catch.catchAngleDeg
    val holdMaxTicks get() = catch.holdMaxTicks
    val holdHeight get() = catch.holdHeight
    val holdForward get() = catch.holdForward
    val holdCrouchHeightOffset get() = catch.holdCrouchHeightOffset
    val holdFirstPersonExtraForward get() = catch.holdFirstPersonExtraForward
    val holdFirstPersonExtraDown get() = catch.holdFirstPersonExtraDown
    val dropDistance get() = catch.dropDistance
    val holdReleaseLockTicks get() = catch.holdReleaseLockTicks
    val actionCooldownTicks get() = catch.actionCooldownTicks
    val diveDurationTicks get() = dive.diveDurationTicks
    val diveCooldownTicks get() = dive.diveCooldownTicks
    val diveRange get() = dive.diveRange
    val diveHalfAngleDeg get() = dive.diveHalfAngleDeg
    val diveSpeed get() = dive.diveSpeed
    val diveCatchMaxSpeed get() = dive.diveCatchMaxSpeed
    val diveDeflectForceScale get() = dive.diveDeflectForceScale
    val punchRange get() = dive.punchRange
    val punchForce get() = dive.punchForce
    val throwShortForce get() = dive.throwShortForce
    val throwLongForceMin get() = dive.throwLongForceMin
    val throwLongForceMax get() = dive.throwLongForceMax
    val throwLongAngleMinDeg get() = dive.throwLongAngleMinDeg
    val throwLongAngleMaxDeg get() = dive.throwLongAngleMaxDeg
    val throwSprintBonus get() = dive.throwSprintBonus

    companion object {
        val CODEC: Codec<GoalkeeperSettings> = RecordCodecBuilder.create { i ->
            i.group(
                GoalkeeperCatchSettings.CODEC.fieldOf("catch").forGetter(GoalkeeperSettings::catch),
                GoalkeeperDiveSettings.CODEC.fieldOf("dive").forGetter(GoalkeeperSettings::dive),
            ).apply(i, ::GoalkeeperSettings)
        }

        val DEFAULT = GoalkeeperSettings()
    }
}
