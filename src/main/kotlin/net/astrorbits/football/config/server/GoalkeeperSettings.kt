package net.astrorbits.football.config.server

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class GoalkeeperCatchSettings(
    val catchRange: Double = 3.5,
    val crouchRangeBonus: Double = 0.3,
    val catchMaxSpeed: Double = 1.8,
    val catchAngleDeg: Double = 120.0,
    val holdHeight: Double = 1.05,
    val holdForward: Double = 0.85,
    val holdCrouchHeightOffset: Double = 0.15,
    val holdFirstPersonExtraForward: Double = 0.15,
    val holdFirstPersonExtraDown: Double = 0.12,
    val dropDistance: Double = 0.5,
    val holdReleaseLockTicks: Int = 25,
    /** 比赛期间守门员持球后，其他球员无法踢走/抢断的时长（tick）；0 关闭。默认 200 tick ≈ 10 秒。 */
    val holdStealProtectionTicks: Int = 200,
    val actionCooldownTicks: Int = 3,
) {
    companion object {
        val CODEC: Codec<GoalkeeperCatchSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.DOUBLE.fieldOf("catch_range").forGetter(GoalkeeperCatchSettings::catchRange),
                Codec.DOUBLE.fieldOf("crouch_range_bonus").forGetter(GoalkeeperCatchSettings::crouchRangeBonus),
                Codec.DOUBLE.fieldOf("catch_max_speed").forGetter(GoalkeeperCatchSettings::catchMaxSpeed),
                Codec.DOUBLE.fieldOf("catch_angle_deg").forGetter(GoalkeeperCatchSettings::catchAngleDeg),
                Codec.DOUBLE.fieldOf("hold_height").forGetter(GoalkeeperCatchSettings::holdHeight),
                Codec.DOUBLE.fieldOf("hold_forward").forGetter(GoalkeeperCatchSettings::holdForward),
                Codec.DOUBLE.fieldOf("hold_crouch_height_offset").forGetter(GoalkeeperCatchSettings::holdCrouchHeightOffset),
                Codec.DOUBLE.fieldOf("hold_first_person_extra_forward").forGetter(GoalkeeperCatchSettings::holdFirstPersonExtraForward),
                Codec.DOUBLE.fieldOf("hold_first_person_extra_down").forGetter(GoalkeeperCatchSettings::holdFirstPersonExtraDown),
                Codec.DOUBLE.fieldOf("drop_distance").forGetter(GoalkeeperCatchSettings::dropDistance),
                Codec.INT.fieldOf("hold_release_lock_ticks").forGetter(GoalkeeperCatchSettings::holdReleaseLockTicks),
                Codec.INT.optionalFieldOf("hold_steal_protection_ticks", 200)
                    .forGetter(GoalkeeperCatchSettings::holdStealProtectionTicks),
                Codec.INT.fieldOf("action_cooldown_ticks").forGetter(GoalkeeperCatchSettings::actionCooldownTicks),
            ).apply(i, ::GoalkeeperCatchSettings)
        }

        val DEFAULT = GoalkeeperCatchSettings()
    }
}

/** 鱼跃基础参数（时长、范围、速度、后坐力阈值等）。 */
data class GoalkeeperDiveBehaviorSettings(
    val diveDurationTicks: Int = 8,
    val diveCooldownTicks: Int = 24,
    val diveRange: Double = 3.0,
    val diveHalfAngleDeg: Double = 60.0,
    /** 接球锥：球相对扑救视线半角小于此值视为“正中”，否则挡出。 */
    val diveCatchCenterHalfAngleDeg: Double = 18.0,
    val diveSpeed: Double = 0.35,
    val diveCatchMaxSpeed: Double = 2.2,
    val diveDeflectForceScale: Double = 0.6,
    val diveCatchRecoilMinSpeed: Double = 0.25,
    val diveCatchOriginEyeScale: Double = 0.65,
    val diveCloseRange: Double = 2.5,
    val diveHighBallMinHeight: Double = 0.6,
    val diveHighBallExtraHalfAngleDeg: Double = 25.0,
    val diveCloseVerticalBelowFeet: Double = 0.3,
    val diveCloseVerticalAboveHead: Double = 1.8,
) {
    companion object {
        val CODEC: Codec<GoalkeeperDiveBehaviorSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.INT.fieldOf("dive_duration_ticks").forGetter(GoalkeeperDiveBehaviorSettings::diveDurationTicks),
                Codec.INT.fieldOf("dive_cooldown_ticks").forGetter(GoalkeeperDiveBehaviorSettings::diveCooldownTicks),
                Codec.DOUBLE.fieldOf("dive_range").forGetter(GoalkeeperDiveBehaviorSettings::diveRange),
                Codec.DOUBLE.fieldOf("dive_half_angle_deg").forGetter(GoalkeeperDiveBehaviorSettings::diveHalfAngleDeg),
                Codec.DOUBLE.optionalFieldOf("dive_catch_center_half_angle_deg", 18.0)
                    .forGetter(GoalkeeperDiveBehaviorSettings::diveCatchCenterHalfAngleDeg),
                Codec.DOUBLE.fieldOf("dive_speed").forGetter(GoalkeeperDiveBehaviorSettings::diveSpeed),
                Codec.DOUBLE.fieldOf("dive_catch_max_speed").forGetter(GoalkeeperDiveBehaviorSettings::diveCatchMaxSpeed),
                Codec.DOUBLE.fieldOf("dive_deflect_force_scale").forGetter(GoalkeeperDiveBehaviorSettings::diveDeflectForceScale),
                Codec.DOUBLE.fieldOf("dive_catch_recoil_min_speed").forGetter(GoalkeeperDiveBehaviorSettings::diveCatchRecoilMinSpeed),
                Codec.DOUBLE.optionalFieldOf("dive_catch_origin_eye_scale", 0.65)
                    .forGetter(GoalkeeperDiveBehaviorSettings::diveCatchOriginEyeScale),
                Codec.DOUBLE.optionalFieldOf("dive_close_range", 2.5)
                    .forGetter(GoalkeeperDiveBehaviorSettings::diveCloseRange),
                Codec.DOUBLE.optionalFieldOf("dive_high_ball_min_height", 0.6)
                    .forGetter(GoalkeeperDiveBehaviorSettings::diveHighBallMinHeight),
                Codec.DOUBLE.optionalFieldOf("dive_high_ball_extra_half_angle_deg", 25.0)
                    .forGetter(GoalkeeperDiveBehaviorSettings::diveHighBallExtraHalfAngleDeg),
                Codec.DOUBLE.optionalFieldOf("dive_close_vertical_below_feet", 0.3)
                    .forGetter(GoalkeeperDiveBehaviorSettings::diveCloseVerticalBelowFeet),
                Codec.DOUBLE.optionalFieldOf("dive_close_vertical_above_head", 1.8)
                    .forGetter(GoalkeeperDiveBehaviorSettings::diveCloseVerticalAboveHead),
            ).apply(i, ::GoalkeeperDiveBehaviorSettings)
        }

        val DEFAULT = GoalkeeperDiveBehaviorSettings()
    }
}

/** 鱼跃俯仰曲线（Minecraft pitch：负=仰视，正=俯视）。 */
data class GoalkeeperDivePitchSettings(
    val groundPitchThresholdDeg: Double = 30.0,
    val lookUpReferencePitchDeg: Double = 45.0,
    val lookUpMaxHeightScale: Double = 1.45,
    val lookUpMinForwardScale: Double = 0.55,
    val groundHeightScale: Double = 0.0,
    val groundForwardScale: Double = 0.38,
    val groundVerticalSpeed: Double = -0.08,
) {
    companion object {
        val CODEC: Codec<GoalkeeperDivePitchSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.DOUBLE.fieldOf("ground_pitch_threshold_deg").forGetter(GoalkeeperDivePitchSettings::groundPitchThresholdDeg),
                Codec.DOUBLE.fieldOf("look_up_reference_pitch_deg").forGetter(GoalkeeperDivePitchSettings::lookUpReferencePitchDeg),
                Codec.DOUBLE.fieldOf("look_up_max_height_scale").forGetter(GoalkeeperDivePitchSettings::lookUpMaxHeightScale),
                Codec.DOUBLE.fieldOf("look_up_min_forward_scale").forGetter(GoalkeeperDivePitchSettings::lookUpMinForwardScale),
                Codec.DOUBLE.fieldOf("ground_height_scale").forGetter(GoalkeeperDivePitchSettings::groundHeightScale),
                Codec.DOUBLE.fieldOf("ground_forward_scale").forGetter(GoalkeeperDivePitchSettings::groundForwardScale),
                Codec.DOUBLE.fieldOf("ground_vertical_speed").forGetter(GoalkeeperDivePitchSettings::groundVerticalSpeed),
            ).apply(i, ::GoalkeeperDivePitchSettings)
        }

        val DEFAULT = GoalkeeperDivePitchSettings()
    }
}

/** 鱼跃蓄力冲量倍率（× dive_speed 或独立竖直速度）。 */
data class GoalkeeperDiveImpulseSettings(
    val launchForwardMinScale: Double = 1.4,
    val launchForwardMaxScale: Double = 3.2,
    val launchUpMin: Double = 0.38,
    val launchUpMax: Double = 0.55,
    val sustainForwardMinScale: Double = 0.9,
    val sustainForwardMaxScale: Double = 2.4,
) {
    companion object {
        val CODEC: Codec<GoalkeeperDiveImpulseSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.DOUBLE.fieldOf("launch_forward_min_scale").forGetter(GoalkeeperDiveImpulseSettings::launchForwardMinScale),
                Codec.DOUBLE.fieldOf("launch_forward_max_scale").forGetter(GoalkeeperDiveImpulseSettings::launchForwardMaxScale),
                Codec.DOUBLE.fieldOf("launch_up_min").forGetter(GoalkeeperDiveImpulseSettings::launchUpMin),
                Codec.DOUBLE.fieldOf("launch_up_max").forGetter(GoalkeeperDiveImpulseSettings::launchUpMax),
                Codec.DOUBLE.fieldOf("sustain_forward_min_scale").forGetter(GoalkeeperDiveImpulseSettings::sustainForwardMinScale),
                Codec.DOUBLE.fieldOf("sustain_forward_max_scale").forGetter(GoalkeeperDiveImpulseSettings::sustainForwardMaxScale),
            ).apply(i, ::GoalkeeperDiveImpulseSettings)
        }

        val DEFAULT = GoalkeeperDiveImpulseSettings()
    }
}

data class GoalkeeperDiveActionsSettings(
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
        val CODEC: Codec<GoalkeeperDiveActionsSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.DOUBLE.fieldOf("punch_range").forGetter(GoalkeeperDiveActionsSettings::punchRange),
                Codec.DOUBLE.fieldOf("punch_force").forGetter(GoalkeeperDiveActionsSettings::punchForce),
                Codec.DOUBLE.fieldOf("throw_short_force").forGetter(GoalkeeperDiveActionsSettings::throwShortForce),
                Codec.DOUBLE.fieldOf("throw_long_force_min").forGetter(GoalkeeperDiveActionsSettings::throwLongForceMin),
                Codec.DOUBLE.fieldOf("throw_long_force_max").forGetter(GoalkeeperDiveActionsSettings::throwLongForceMax),
                Codec.DOUBLE.fieldOf("throw_long_angle_min_deg").forGetter(GoalkeeperDiveActionsSettings::throwLongAngleMinDeg),
                Codec.DOUBLE.fieldOf("throw_long_angle_max_deg").forGetter(GoalkeeperDiveActionsSettings::throwLongAngleMaxDeg),
                Codec.DOUBLE.fieldOf("throw_sprint_bonus").forGetter(GoalkeeperDiveActionsSettings::throwSprintBonus),
            ).apply(i, ::GoalkeeperDiveActionsSettings)
        }

        val DEFAULT = GoalkeeperDiveActionsSettings()
    }
}

data class GoalkeeperDiveSettings(
    val behavior: GoalkeeperDiveBehaviorSettings = GoalkeeperDiveBehaviorSettings.DEFAULT,
    val pitch: GoalkeeperDivePitchSettings = GoalkeeperDivePitchSettings.DEFAULT,
    val impulse: GoalkeeperDiveImpulseSettings = GoalkeeperDiveImpulseSettings.DEFAULT,
    val actions: GoalkeeperDiveActionsSettings = GoalkeeperDiveActionsSettings.DEFAULT,
) {
    val diveDurationTicks get() = behavior.diveDurationTicks
    val diveCooldownTicks get() = behavior.diveCooldownTicks
    val diveRange get() = behavior.diveRange
    val diveHalfAngleDeg get() = behavior.diveHalfAngleDeg
    val diveCatchCenterHalfAngleDeg get() = behavior.diveCatchCenterHalfAngleDeg
    val diveSpeed get() = behavior.diveSpeed
    val diveCatchMaxSpeed get() = behavior.diveCatchMaxSpeed
    val diveDeflectForceScale get() = behavior.diveDeflectForceScale
    val diveCatchRecoilMinSpeed get() = behavior.diveCatchRecoilMinSpeed
    val diveCatchOriginEyeScale get() = behavior.diveCatchOriginEyeScale
    val diveCloseRange get() = behavior.diveCloseRange
    val diveHighBallMinHeight get() = behavior.diveHighBallMinHeight
    val diveHighBallExtraHalfAngleDeg get() = behavior.diveHighBallExtraHalfAngleDeg
    val diveCloseVerticalBelowFeet get() = behavior.diveCloseVerticalBelowFeet
    val diveCloseVerticalAboveHead get() = behavior.diveCloseVerticalAboveHead
    val punchRange get() = actions.punchRange
    val punchForce get() = actions.punchForce
    val throwShortForce get() = actions.throwShortForce
    val throwLongForceMin get() = actions.throwLongForceMin
    val throwLongForceMax get() = actions.throwLongForceMax
    val throwLongAngleMinDeg get() = actions.throwLongAngleMinDeg
    val throwLongAngleMaxDeg get() = actions.throwLongAngleMaxDeg
    val throwSprintBonus get() = actions.throwSprintBonus

    companion object {
        val CODEC: Codec<GoalkeeperDiveSettings> = RecordCodecBuilder.create { i ->
            i.group(
                GoalkeeperDiveBehaviorSettings.CODEC.optionalFieldOf("behavior", GoalkeeperDiveBehaviorSettings.DEFAULT)
                    .forGetter(GoalkeeperDiveSettings::behavior),
                GoalkeeperDivePitchSettings.CODEC.optionalFieldOf("pitch", GoalkeeperDivePitchSettings.DEFAULT)
                    .forGetter(GoalkeeperDiveSettings::pitch),
                GoalkeeperDiveImpulseSettings.CODEC.optionalFieldOf("impulse", GoalkeeperDiveImpulseSettings.DEFAULT)
                    .forGetter(GoalkeeperDiveSettings::impulse),
                GoalkeeperDiveActionsSettings.CODEC.optionalFieldOf("actions", GoalkeeperDiveActionsSettings.DEFAULT)
                    .forGetter(GoalkeeperDiveSettings::actions),
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
    val holdHeight get() = catch.holdHeight
    val holdForward get() = catch.holdForward
    val holdCrouchHeightOffset get() = catch.holdCrouchHeightOffset
    val holdFirstPersonExtraForward get() = catch.holdFirstPersonExtraForward
    val holdFirstPersonExtraDown get() = catch.holdFirstPersonExtraDown
    val dropDistance get() = catch.dropDistance
    val holdReleaseLockTicks get() = catch.holdReleaseLockTicks
    val holdStealProtectionTicks get() = catch.holdStealProtectionTicks
    val actionCooldownTicks get() = catch.actionCooldownTicks
    val diveDurationTicks get() = dive.diveDurationTicks
    val diveCooldownTicks get() = dive.diveCooldownTicks
    val diveRange get() = dive.diveRange
    val diveHalfAngleDeg get() = dive.diveHalfAngleDeg
    val diveCatchCenterHalfAngleDeg get() = dive.diveCatchCenterHalfAngleDeg
    val diveSpeed get() = dive.diveSpeed
    val diveCatchMaxSpeed get() = dive.diveCatchMaxSpeed
    val diveDeflectForceScale get() = dive.diveDeflectForceScale
    val diveCatchRecoilMinSpeed get() = dive.diveCatchRecoilMinSpeed
    val diveCatchOriginEyeScale get() = dive.diveCatchOriginEyeScale
    val diveCloseRange get() = dive.diveCloseRange
    val diveHighBallMinHeight get() = dive.diveHighBallMinHeight
    val diveHighBallExtraHalfAngleDeg get() = dive.diveHighBallExtraHalfAngleDeg
    val diveCloseVerticalBelowFeet get() = dive.diveCloseVerticalBelowFeet
    val diveCloseVerticalAboveHead get() = dive.diveCloseVerticalAboveHead
    val divePitch get() = dive.pitch
    val diveImpulse get() = dive.impulse
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
