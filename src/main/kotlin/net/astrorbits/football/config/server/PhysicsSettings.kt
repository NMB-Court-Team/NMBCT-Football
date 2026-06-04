package net.astrorbits.football.config.server

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class PhysicsCoreSettings(
    val radius: Double = 0.25,
    val mass: Double = 0.5,
    val inertia: Double = 2.0 / 5.0 * 0.5 * 0.25 * 0.25,
    val gravity: Double = 0.04,
    val airDrag: Double = 0.99,
    val spinDrag: Double = 0.995,
    val groundFriction: Double = 0.92,
    val groundSpinFriction: Double = 0.92,
    val groundYawSpinFriction: Double = 0.65,
    val rollCoupling: Double = 0.15,
    val stopSpeedSqr: Double = 1.0e-6,
    val epsilon: Double = 1.0e-4,
) {
    companion object {
        val CODEC: Codec<PhysicsCoreSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.DOUBLE.fieldOf("radius").forGetter(PhysicsCoreSettings::radius),
                Codec.DOUBLE.fieldOf("mass").forGetter(PhysicsCoreSettings::mass),
                Codec.DOUBLE.fieldOf("inertia").forGetter(PhysicsCoreSettings::inertia),
                Codec.DOUBLE.fieldOf("gravity").forGetter(PhysicsCoreSettings::gravity),
                Codec.DOUBLE.fieldOf("air_drag").forGetter(PhysicsCoreSettings::airDrag),
                Codec.DOUBLE.fieldOf("spin_drag").forGetter(PhysicsCoreSettings::spinDrag),
                Codec.DOUBLE.fieldOf("ground_friction").forGetter(PhysicsCoreSettings::groundFriction),
                Codec.DOUBLE.fieldOf("ground_spin_friction").forGetter(PhysicsCoreSettings::groundSpinFriction),
                Codec.DOUBLE.fieldOf("ground_yaw_spin_friction").forGetter(PhysicsCoreSettings::groundYawSpinFriction),
                Codec.DOUBLE.fieldOf("roll_coupling").forGetter(PhysicsCoreSettings::rollCoupling),
                Codec.DOUBLE.fieldOf("stop_speed_sqr").forGetter(PhysicsCoreSettings::stopSpeedSqr),
                Codec.DOUBLE.fieldOf("epsilon").forGetter(PhysicsCoreSettings::epsilon),
            ).apply(i, ::PhysicsCoreSettings)
        }

        val DEFAULT = PhysicsCoreSettings()
    }
}

data class PhysicsCollisionSettings(
    val restitution: Double = 0.68,
    val wallRestitution: Double = 0.68,
    val wallSpinRetention: Double = 0.3,
    val wallBounceCooldownTicks: Int = 5,
    val wallBlockRatio: Double = 0.35,
    val wallYawSpinDamp: Double = 0.35,
    val stuckSpinDrag: Double = 0.65,
    val groundSettleVy: Double = 0.08,
    val cobwebHorizontalDrag: Double = 0.25,
    val cobwebVerticalDrag: Double = 0.05,
    val cobwebSpinDrag: Double = 0.5,
    val bounceSoundMinWallSpeed: Double = 0.08,
    val bounceSoundMinGroundVy: Double = 0.12,
) {
    companion object {
        val CODEC: Codec<PhysicsCollisionSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.DOUBLE.fieldOf("restitution").forGetter(PhysicsCollisionSettings::restitution),
                Codec.DOUBLE.fieldOf("wall_restitution").forGetter(PhysicsCollisionSettings::wallRestitution),
                Codec.DOUBLE.fieldOf("wall_spin_retention").forGetter(PhysicsCollisionSettings::wallSpinRetention),
                Codec.INT.fieldOf("wall_bounce_cooldown_ticks").forGetter(PhysicsCollisionSettings::wallBounceCooldownTicks),
                Codec.DOUBLE.fieldOf("wall_block_ratio").forGetter(PhysicsCollisionSettings::wallBlockRatio),
                Codec.DOUBLE.fieldOf("wall_yaw_spin_damp").forGetter(PhysicsCollisionSettings::wallYawSpinDamp),
                Codec.DOUBLE.fieldOf("stuck_spin_drag").forGetter(PhysicsCollisionSettings::stuckSpinDrag),
                Codec.DOUBLE.fieldOf("ground_settle_vy").forGetter(PhysicsCollisionSettings::groundSettleVy),
                Codec.DOUBLE.fieldOf("cobweb_horizontal_drag").forGetter(PhysicsCollisionSettings::cobwebHorizontalDrag),
                Codec.DOUBLE.fieldOf("cobweb_vertical_drag").forGetter(PhysicsCollisionSettings::cobwebVerticalDrag),
                Codec.DOUBLE.fieldOf("cobweb_spin_drag").forGetter(PhysicsCollisionSettings::cobwebSpinDrag),
                Codec.DOUBLE.fieldOf("bounce_sound_min_wall_speed").forGetter(PhysicsCollisionSettings::bounceSoundMinWallSpeed),
                Codec.DOUBLE.fieldOf("bounce_sound_min_ground_vy").forGetter(PhysicsCollisionSettings::bounceSoundMinGroundVy),
            ).apply(i, ::PhysicsCollisionSettings)
        }

        val DEFAULT = PhysicsCollisionSettings()
    }
}

data class PhysicsKickSettings(
    val kickForceScale: Double = 0.18,
    val kickMovingLateralDamp: Double = 0.15,
    val orientationResetVelocityDelta: Double = 0.06,
    val orientationResetOmegaDelta: Double = 0.06,
) {
    companion object {
        val CODEC: Codec<PhysicsKickSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.DOUBLE.fieldOf("kick_force_scale").forGetter(PhysicsKickSettings::kickForceScale),
                Codec.DOUBLE.fieldOf("kick_moving_lateral_damp").forGetter(PhysicsKickSettings::kickMovingLateralDamp),
                Codec.DOUBLE.fieldOf("orientation_reset_velocity_delta").forGetter(PhysicsKickSettings::orientationResetVelocityDelta),
                Codec.DOUBLE.fieldOf("orientation_reset_omega_delta").forGetter(PhysicsKickSettings::orientationResetOmegaDelta),
            ).apply(i, ::PhysicsKickSettings)
        }

        val DEFAULT = PhysicsKickSettings()
    }
}

/** 足球物理模拟（服务端权威）。 */
data class PhysicsSettings(
    val core: PhysicsCoreSettings = PhysicsCoreSettings.DEFAULT,
    val collision: PhysicsCollisionSettings = PhysicsCollisionSettings.DEFAULT,
    val kick: PhysicsKickSettings = PhysicsKickSettings.DEFAULT,
) {
    val radius get() = core.radius
    val mass get() = core.mass
    val inertia get() = core.inertia
    val gravity get() = core.gravity
    val airDrag get() = core.airDrag
    val spinDrag get() = core.spinDrag
    val groundFriction get() = core.groundFriction
    val groundSpinFriction get() = core.groundSpinFriction
    val groundYawSpinFriction get() = core.groundYawSpinFriction
    val rollCoupling get() = core.rollCoupling
    val stopSpeedSqr get() = core.stopSpeedSqr
    val epsilon get() = core.epsilon
    val restitution get() = collision.restitution
    val wallRestitution get() = collision.wallRestitution
    val wallSpinRetention get() = collision.wallSpinRetention
    val wallBounceCooldownTicks get() = collision.wallBounceCooldownTicks
    val wallBlockRatio get() = collision.wallBlockRatio
    val wallYawSpinDamp get() = collision.wallYawSpinDamp
    val stuckSpinDrag get() = collision.stuckSpinDrag
    val groundSettleVy get() = collision.groundSettleVy
    val cobwebHorizontalDrag get() = collision.cobwebHorizontalDrag
    val cobwebVerticalDrag get() = collision.cobwebVerticalDrag
    val cobwebSpinDrag get() = collision.cobwebSpinDrag
    val bounceSoundMinWallSpeed get() = collision.bounceSoundMinWallSpeed
    val bounceSoundMinGroundVy get() = collision.bounceSoundMinGroundVy
    val kickForceScale get() = kick.kickForceScale
    val kickMovingLateralDamp get() = kick.kickMovingLateralDamp
    val orientationResetVelocityDelta get() = kick.orientationResetVelocityDelta
    val orientationResetOmegaDelta get() = kick.orientationResetOmegaDelta

    companion object {
        val CODEC: Codec<PhysicsSettings> = RecordCodecBuilder.create { i ->
            i.group(
                PhysicsCoreSettings.CODEC.fieldOf("core").forGetter(PhysicsSettings::core),
                PhysicsCollisionSettings.CODEC.fieldOf("collision").forGetter(PhysicsSettings::collision),
                PhysicsKickSettings.CODEC.fieldOf("kick").forGetter(PhysicsSettings::kick),
            ).apply(i, ::PhysicsSettings)
        }

        val DEFAULT = PhysicsSettings()
    }
}
