package net.astrorbits.football.client.config.yacl

import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.OptionGroup
import dev.isxander.yacl3.api.YetAnotherConfigLib
import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.config.server.*
import net.astrorbits.football.client.util.YaclOptionUtil.addDouble
import net.astrorbits.football.client.util.YaclOptionUtil.addInt
import net.astrorbits.football.client.util.YaclOptionUtil.addLong
import net.astrorbits.football.network.ServerConfigApplyC2SPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

object FootballServerConfigScreen {
    fun create(parent: Screen?, initial: FootballServerConfig): Screen {
        var draft = initial

        return YetAnotherConfigLib.createBuilder()
            .title(Component.translatable("yacl3.config.$MOD_ID.server.title"))
            .category(physicsCategory({ draft }, { draft = it }))
            .category(playerCategory({ draft }, { draft = it }))
            .category(goalkeeperCategory({ draft }, { draft = it }))
            .category(particlesCategory({ draft }, { draft = it }))
            .save {
                if (ClientPlayNetworking.canSend(ServerConfigApplyC2SPayload.TYPE)) {
                    ClientPlayNetworking.send(ServerConfigApplyC2SPayload(draft))
                }
            }
            .build()
            .generateScreen(parent)
    }

    private fun physicsCategory(
        getter: () -> FootballServerConfig,
        setter: (FootballServerConfig) -> Unit,
    ): ConfigCategory = ConfigCategory.createBuilder()
        .name(Component.translatable("yacl3.config.$MOD_ID.server.category.physics"))
        .group(
            OptionGroup.createBuilder()
                .name(Component.translatable("yacl3.config.$MOD_ID.server.group.physics_core"))
                .apply { addPhysicsCoreOptions(getter, setter) }
                .build(),
        )
        .group(
            OptionGroup.createBuilder()
                .name(Component.translatable("yacl3.config.$MOD_ID.server.group.physics_collision"))
                .apply { addPhysicsCollisionOptions(getter, setter) }
                .build(),
        )
        .group(
            OptionGroup.createBuilder()
                .name(Component.translatable("yacl3.config.$MOD_ID.server.group.physics_kick"))
                .apply { addPhysicsKickOptions(getter, setter) }
                .build(),
        )
        .build()

    private fun OptionGroup.Builder.addPhysicsCoreOptions(
        cfg: () -> FootballServerConfig,
        setCfg: (FootballServerConfig) -> Unit,
    ) {
        fun core(): PhysicsCoreSettings = cfg().physics.core
        fun setCore(t: (PhysicsCoreSettings) -> PhysicsCoreSettings) {
            val p = cfg().physics
            setCfg(cfg().copy(physics = p.copy(core = t(core()))))
        }

        addDouble("$P.radius", "$P.radius.desc", PHY_CORE.radius, { core().radius }, { v -> setCore { it.copy(radius = v) } }, 0.1..1.0)
        addDouble("$P.mass", "$P.mass.desc", PHY_CORE.mass, { core().mass }, { v -> setCore { it.copy(mass = v) } }, 0.05..2.0)
        addDouble("$P.gravity", "$P.gravity.desc", PHY_CORE.gravity, { core().gravity }, { v -> setCore { it.copy(gravity = v) } }, 0.0..0.2)
        addDouble("$P.air_drag", "$P.air_drag.desc", PHY_CORE.airDrag, { core().airDrag }, { v -> setCore { it.copy(airDrag = v) } }, 0.8..1.0)
        addDouble("$P.spin_drag", "$P.spin_drag.desc", PHY_CORE.spinDrag, { core().spinDrag }, { v -> setCore { it.copy(spinDrag = v) } }, 0.9..1.0)
        addDouble("$P.ground_friction", "$P.ground_friction.desc", PHY_CORE.groundFriction, { core().groundFriction }, { v -> setCore { it.copy(groundFriction = v) } }, 0.5..1.0)
        addDouble("$P.roll_coupling", "$P.roll_coupling.desc", PHY_CORE.rollCoupling, { core().rollCoupling }, { v -> setCore { it.copy(rollCoupling = v) } }, 0.0..1.0)
    }

    private fun OptionGroup.Builder.addPhysicsCollisionOptions(
        cfg: () -> FootballServerConfig,
        setCfg: (FootballServerConfig) -> Unit,
    ) {
        fun col(): PhysicsCollisionSettings = cfg().physics.collision
        fun setCol(t: (PhysicsCollisionSettings) -> PhysicsCollisionSettings) {
            val p = cfg().physics
            setCfg(cfg().copy(physics = p.copy(collision = t(col()))))
        }

        addDouble("$P.restitution", "$P.restitution.desc", PHY_COL.restitution, { col().restitution }, { v -> setCol { it.copy(restitution = v) } }, 0.0..1.0)
        addDouble("$P.wall_restitution", "$P.wall_restitution.desc", PHY_COL.wallRestitution, { col().wallRestitution }, { v -> setCol { it.copy(wallRestitution = v) } }, 0.0..1.0)
        addDouble("$P.wall_spin_retention", "$P.wall_spin_retention.desc", PHY_COL.wallSpinRetention, { col().wallSpinRetention }, { v -> setCol { it.copy(wallSpinRetention = v) } }, 0.0..1.0)
        addInt("$P.wall_bounce_cooldown", "$P.wall_bounce_cooldown.desc", PHY_COL.wallBounceCooldownTicks, { col().wallBounceCooldownTicks }, { v -> setCol { it.copy(wallBounceCooldownTicks = v) } }, 0..30)
        addDouble("$P.ground_settle_vy", "$P.ground_settle_vy.desc", PHY_COL.groundSettleVy, { col().groundSettleVy }, { v -> setCol { it.copy(groundSettleVy = v) } }, 0.0..0.5)
        addDouble("$P.cobweb_horizontal", "$P.cobweb_horizontal.desc", PHY_COL.cobwebHorizontalDrag, { col().cobwebHorizontalDrag }, { v -> setCol { it.copy(cobwebHorizontalDrag = v) } }, 0.0..1.0)
    }

    private fun OptionGroup.Builder.addPhysicsKickOptions(
        cfg: () -> FootballServerConfig,
        setCfg: (FootballServerConfig) -> Unit,
    ) {
        fun kick(): PhysicsKickSettings = cfg().physics.kick
        fun setKick(t: (PhysicsKickSettings) -> PhysicsKickSettings) {
            val p = cfg().physics
            setCfg(cfg().copy(physics = p.copy(kick = t(kick()))))
        }

        addDouble("$P.kick_force_scale", "$P.kick_force_scale.desc", PHY_KICK.kickForceScale, { kick().kickForceScale }, { v -> setKick { it.copy(kickForceScale = v) } }, 0.01..1.0)
        addDouble("$P.kick_moving_lateral_damp", "$P.kick_moving_lateral_damp.desc", PHY_KICK.kickMovingLateralDamp, { kick().kickMovingLateralDamp }, { v -> setKick { it.copy(kickMovingLateralDamp = v) } }, 0.0..1.0)
        addDouble("$P.orientation_reset_velocity", "$P.orientation_reset_velocity.desc", PHY_KICK.orientationResetVelocityDelta, { kick().orientationResetVelocityDelta }, { v -> setKick { it.copy(orientationResetVelocityDelta = v) } }, 0.0..1.0)
        addDouble("$P.orientation_reset_omega", "$P.orientation_reset_omega.desc", PHY_KICK.orientationResetOmegaDelta, { kick().orientationResetOmegaDelta }, { v -> setKick { it.copy(orientationResetOmegaDelta = v) } }, 0.0..1.0)
    }

    private fun playerCategory(
        getter: () -> FootballServerConfig,
        setter: (FootballServerConfig) -> Unit,
    ): ConfigCategory = ConfigCategory.createBuilder()
        .name(Component.translatable("yacl3.config.$MOD_ID.server.category.player"))
        .group(
            OptionGroup.createBuilder()
                .name(Component.translatable("yacl3.config.$MOD_ID.server.group.player_kick"))
                .apply {
                    fun kick(): PlayerKickSettings = getter().playerInput.kick
                    fun setKick(t: (PlayerKickSettings) -> PlayerKickSettings) {
                        val pi = getter().playerInput
                        setter(getter().copy(playerInput = pi.copy(kick = t(kick()))))
                    }

                    addDouble("$PI.player_kick_range", "$PI.player_kick_range.desc", PL_KICK.playerKickRange, { kick().playerKickRange }, { v -> setKick { it.copy(playerKickRange = v) } }, 0.5..8.0)
                    addDouble("$PI.command_kick_range", "$PI.command_kick_range.desc", PL_KICK.commandKickRange, { kick().commandKickRange }, { v -> setKick { it.copy(commandKickRange = v) } }, 0.5..10.0)
                    addDouble("$PI.pass_force", "$PI.pass_force.desc", PL_KICK.passForce, { kick().passForce }, { v -> setKick { it.copy(passForce = v) } }, 0.1..10.0)
                    addDouble("$PI.shoot_force_min", "$PI.shoot_force_min.desc", PL_KICK.shootForceMin, { kick().shootForceMin }, { v -> setKick { it.copy(shootForceMin = v) } }, 0.1..10.0)
                    addDouble("$PI.shoot_force_max", "$PI.shoot_force_max.desc", PL_KICK.shootForceMax, { kick().shootForceMax }, { v -> setKick { it.copy(shootForceMax = v) } }, 0.1..15.0)
                    addDouble("$PI.chip_force", "$PI.chip_force.desc", PL_KICK.chipForce, { kick().chipForce }, { v -> setKick { it.copy(chip = it.chip.copy(chipForce = v)) } }, 0.1..10.0)
                    addInt("$PI.action_cooldown", "$PI.action_cooldown.desc", PL_KICK.actionCooldownTicks, { kick().actionCooldownTicks }, { v -> setKick { it.copy(actionCooldownTicks = v) } }, 0..40)
                }
                .build(),
        )
        .group(
            OptionGroup.createBuilder()
                .name(Component.translatable("yacl3.config.$MOD_ID.server.group.player_charge"))
                .apply {
                    fun charge(): KickChargeSettings = getter().playerInput.charge
                    fun setCharge(t: (KickChargeSettings) -> KickChargeSettings) {
                        val pi = getter().playerInput
                        setter(getter().copy(playerInput = pi.copy(charge = t(charge()))))
                    }

                    addLong(
                        "$PI.tap_max_ms",
                        "$PI.tap_max_ms.desc",
                        PL_CHARGE.tapMaxMs,
                        { charge().tapMaxMs },
                        { v -> setCharge { it.copy(tapMaxMs = v) } },
                        50L..800L,
                    )
                    addLong(
                        "$PI.charge_min_ms",
                        "$PI.charge_min_ms.desc",
                        PL_CHARGE.chargeMinMs,
                        { charge().chargeMinMs },
                        { v -> setCharge { it.copy(chargeMinMs = v) } },
                        100L..2000L,
                    )
                    addLong(
                        "$PI.charge_rise_ms",
                        "$PI.charge_rise_ms.desc",
                        PL_CHARGE.chargeRiseMs,
                        { charge().chargeRiseMs },
                        { v -> setCharge { it.copy(chargeRiseMs = v) } },
                        200L..5000L,
                    )
                    addLong(
                        "$PI.charge_perfect_window_ms",
                        "$PI.charge_perfect_window_ms.desc",
                        PL_CHARGE.chargePerfectWindowMs,
                        { charge().chargePerfectWindowMs },
                        { v -> setCharge { it.copy(chargePerfectWindowMs = v) } },
                        30L..800L,
                    )
                    addLong(
                        "$PI.charge_decay_ms",
                        "$PI.charge_decay_ms.desc",
                        PL_CHARGE.chargeDecayMs,
                        { charge().chargeDecayMs },
                        { v -> setCharge { it.copy(chargeDecayMs = v) } },
                        300L..5000L,
                    )
                    addDouble(
                        "$PI.kick_spread_inaccuracy",
                        "$PI.kick_spread_inaccuracy.desc",
                        PL_CHARGE.kickSpreadInaccuracy,
                        { charge().kickSpreadInaccuracy },
                        { v -> setCharge { it.copy(kickSpreadInaccuracy = v) } },
                        0.0..10.0,
                        step = 0.1,
                    )
                    addDouble(
                        "$PI.perfect_charge_force_bonus",
                        "$PI.perfect_charge_force_bonus.desc",
                        PL_CHARGE.perfectChargeForceBonus,
                        { charge().perfectChargeForceBonus },
                        { v -> setCharge { it.copy(perfectChargeForceBonus = v) } },
                        1.0..1.5,
                        step = 0.01,
                    )
                }
                .build(),
        )
        .group(
            OptionGroup.createBuilder()
                .name(Component.translatable("yacl3.config.$MOD_ID.server.group.player_dribble"))
                .apply {
                    fun dribble(): PlayerDribbleSettings = getter().playerInput.dribble
                    fun setDribble(t: (PlayerDribbleSettings) -> PlayerDribbleSettings) {
                        val pi = getter().playerInput
                        setter(getter().copy(playerInput = pi.copy(dribble = t(dribble()))))
                    }

                    addDouble("$PI.dribble_target_distance", "$PI.dribble_target_distance.desc", PL_DRIBBLE.dribbleTargetDistance, { dribble().dribbleTargetDistance }, { v -> setDribble { it.copy(dribbleTargetDistance = v) } }, 0.1..3.0)
                    addDouble("$PI.dribble_max_control_range", "$PI.dribble_max_control_range.desc", PL_DRIBBLE.dribbleMaxControlRange, { dribble().dribbleMaxControlRange }, { v -> setDribble { it.copy(dribbleMaxControlRange = v) } }, 0.5..8.0)
                    addInt("$PI.dribble_session_timeout", "$PI.dribble_session_timeout.desc", PL_DRIBBLE.dribbleSessionTimeoutTicks, { dribble().dribbleSessionTimeoutTicks }, { v -> setDribble { it.copy(dribbleSessionTimeoutTicks = v) } }, 1..40)
                    addDouble("$PI.dribble_position_gain", "$PI.dribble_position_gain.desc", PL_DRIBBLE.dribblePositionGain, { dribble().dribblePositionGain }, { v -> setDribble { it.copy(dribblePositionGain = v) } }, 0.0..2.0)
                    addDouble("$PI.dribble_velocity_gain", "$PI.dribble_velocity_gain.desc", PL_DRIBBLE.dribbleVelocityGain, { dribble().dribbleVelocityGain }, { v -> setDribble { it.copy(dribbleVelocityGain = v) } }, 0.0..2.0)
                    addDouble("$PI.dribble_touch_force", "$PI.dribble_touch_force.desc", PL_DRIBBLE.dribbleTouchForce, { dribble().dribbleTouchForce }, { v -> setDribble { it.copy(dribbleTouchForce = v) } }, 0.0..1.0)
                }
                .build(),
        )
        .build()

    private fun goalkeeperCategory(
        getter: () -> FootballServerConfig,
        setter: (FootballServerConfig) -> Unit,
    ): ConfigCategory = ConfigCategory.createBuilder()
        .name(Component.translatable("yacl3.config.$MOD_ID.server.category.goalkeeper"))
        .group(
            OptionGroup.createBuilder()
                .name(Component.translatable("yacl3.config.$MOD_ID.server.group.gk_catch"))
                .apply {
                    fun gk(): GoalkeeperCatchSettings = getter().goalkeeper.catch
                    fun setGk(t: (GoalkeeperCatchSettings) -> GoalkeeperCatchSettings) {
                        val g = getter().goalkeeper
                        setter(getter().copy(goalkeeper = g.copy(catch = t(gk()))))
                    }

                    addDouble("$GK.catch_range", "$GK.catch_range.desc", GK_CATCH.catchRange, { gk().catchRange }, { v -> setGk { it.copy(catchRange = v) } }, 1.0..10.0)
                    addDouble("$GK.catch_max_speed", "$GK.catch_max_speed.desc", GK_CATCH.catchMaxSpeed, { gk().catchMaxSpeed }, { v -> setGk { it.copy(catchMaxSpeed = v) } }, 0.5..10.0)
                    addInt("$GK.hold_max_ticks", "$GK.hold_max_ticks.desc", GK_CATCH.holdMaxTicks, { gk().holdMaxTicks }, { v -> setGk { it.copy(holdMaxTicks = v) } }, 20..1200)
                    addInt("$GK.hold_release_lock", "$GK.hold_release_lock.desc", GK_CATCH.holdReleaseLockTicks, { gk().holdReleaseLockTicks }, { v -> setGk { it.copy(holdReleaseLockTicks = v) } }, 0..200)
                }
                .build(),
        )
        .group(
            OptionGroup.createBuilder()
                .name(Component.translatable("yacl3.config.$MOD_ID.server.group.gk_dive"))
                .apply {
                    fun gk(): GoalkeeperDiveSettings = getter().goalkeeper.dive
                    fun setGk(t: (GoalkeeperDiveSettings) -> GoalkeeperDiveSettings) {
                        val g = getter().goalkeeper
                        setter(getter().copy(goalkeeper = g.copy(dive = t(gk()))))
                    }
                    fun setBehavior(t: (GoalkeeperDiveBehaviorSettings) -> GoalkeeperDiveBehaviorSettings) {
                        setGk { it.copy(behavior = t(it.behavior)) }
                    }
                    fun setActions(t: (GoalkeeperDiveActionsSettings) -> GoalkeeperDiveActionsSettings) {
                        setGk { it.copy(actions = t(it.actions)) }
                    }

                    addDouble("$GK.dive_range", "$GK.dive_range.desc", GK_DIVE_BEH.diveRange, { gk().diveRange }, { v -> setBehavior { it.copy(diveRange = v) } }, 1.0..10.0)
                    addDouble("$GK.dive_half_angle", "$GK.dive_half_angle.desc", GK_DIVE_BEH.diveHalfAngleDeg, { gk().diveHalfAngleDeg }, { v -> setBehavior { it.copy(diveHalfAngleDeg = v) } }, 15.0..90.0)
                    addDouble("$GK.dive_speed", "$GK.dive_speed.desc", GK_DIVE_BEH.diveSpeed, { gk().diveSpeed }, { v -> setBehavior { it.copy(diveSpeed = v) } }, 0.05..2.0)
                    addInt("$GK.dive_duration", "$GK.dive_duration.desc", GK_DIVE_BEH.diveDurationTicks, { gk().diveDurationTicks }, { v -> setBehavior { it.copy(diveDurationTicks = v) } }, 1..40)
                    addInt("$GK.dive_cooldown", "$GK.dive_cooldown.desc", GK_DIVE_BEH.diveCooldownTicks, { gk().diveCooldownTicks }, { v -> setBehavior { it.copy(diveCooldownTicks = v) } }, 0..200)
                    addDouble("$GK.dive_recoil_min_speed", "$GK.dive_recoil_min_speed.desc", GK_DIVE_BEH.diveCatchRecoilMinSpeed, { gk().diveCatchRecoilMinSpeed }, { v -> setBehavior { it.copy(diveCatchRecoilMinSpeed = v) } }, 0.0..3.0)
                    addDouble("$GK.punch_force", "$GK.punch_force.desc", GK_DIVE_ACT.punchForce, { gk().punchForce }, { v -> setActions { it.copy(punchForce = v) } }, 0.1..10.0)
                }
                .build(),
        )
        .group(
            OptionGroup.createBuilder()
                .name(Component.translatable("yacl3.config.$MOD_ID.server.group.gk_dive_pitch"))
                .apply {
                    fun gk(): GoalkeeperDiveSettings = getter().goalkeeper.dive
                    fun setPitch(t: (GoalkeeperDivePitchSettings) -> GoalkeeperDivePitchSettings) {
                        val g = getter().goalkeeper
                        setter(getter().copy(goalkeeper = g.copy(dive = gk().copy(pitch = t(gk().pitch)))))
                    }

                    fun pitch(): GoalkeeperDivePitchSettings = gk().pitch

                    addDouble("$GK.dive_ground_pitch", "$GK.dive_ground_pitch.desc", GK_DIVE_PITCH.groundPitchThresholdDeg, { pitch().groundPitchThresholdDeg }, { v -> setPitch { it.copy(groundPitchThresholdDeg = v) } }, 5.0..60.0)
                    addDouble("$GK.dive_look_up_pitch", "$GK.dive_look_up_pitch.desc", GK_DIVE_PITCH.lookUpReferencePitchDeg, { pitch().lookUpReferencePitchDeg }, { v -> setPitch { it.copy(lookUpReferencePitchDeg = v) } }, 10.0..90.0)
                    addDouble("$GK.dive_look_up_height", "$GK.dive_look_up_height.desc", GK_DIVE_PITCH.lookUpMaxHeightScale, { pitch().lookUpMaxHeightScale }, { v -> setPitch { it.copy(lookUpMaxHeightScale = v) } }, 1.0..3.0)
                    addDouble("$GK.dive_look_up_forward", "$GK.dive_look_up_forward.desc", GK_DIVE_PITCH.lookUpMinForwardScale, { pitch().lookUpMinForwardScale }, { v -> setPitch { it.copy(lookUpMinForwardScale = v) } }, 0.1..1.0)
                    addDouble("$GK.dive_ground_height", "$GK.dive_ground_height.desc", GK_DIVE_PITCH.groundHeightScale, { pitch().groundHeightScale }, { v -> setPitch { it.copy(groundHeightScale = v) } }, 0.0..1.0)
                    addDouble("$GK.dive_ground_forward", "$GK.dive_ground_forward.desc", GK_DIVE_PITCH.groundForwardScale, { pitch().groundForwardScale }, { v -> setPitch { it.copy(groundForwardScale = v) } }, 0.1..1.0)
                    addDouble("$GK.dive_ground_vertical", "$GK.dive_ground_vertical.desc", GK_DIVE_PITCH.groundVerticalSpeed, { pitch().groundVerticalSpeed }, { v -> setPitch { it.copy(groundVerticalSpeed = v) } }, -0.5..0.2)
                }
                .build(),
        )
        .group(
            OptionGroup.createBuilder()
                .name(Component.translatable("yacl3.config.$MOD_ID.server.group.gk_dive_impulse"))
                .apply {
                    fun gk(): GoalkeeperDiveSettings = getter().goalkeeper.dive
                    fun setImpulse(t: (GoalkeeperDiveImpulseSettings) -> GoalkeeperDiveImpulseSettings) {
                        val g = getter().goalkeeper
                        setter(getter().copy(goalkeeper = g.copy(dive = gk().copy(impulse = t(gk().impulse)))))
                    }

                    fun impulse(): GoalkeeperDiveImpulseSettings = gk().impulse

                    addDouble("$GK.dive_launch_fwd_min", "$GK.dive_launch_fwd_min.desc", GK_DIVE_IMP.launchForwardMinScale, { impulse().launchForwardMinScale }, { v -> setImpulse { it.copy(launchForwardMinScale = v) } }, 0.1..5.0)
                    addDouble("$GK.dive_launch_fwd_max", "$GK.dive_launch_fwd_max.desc", GK_DIVE_IMP.launchForwardMaxScale, { impulse().launchForwardMaxScale }, { v -> setImpulse { it.copy(launchForwardMaxScale = v) } }, 0.1..6.0)
                    addDouble("$GK.dive_launch_up_min", "$GK.dive_launch_up_min.desc", GK_DIVE_IMP.launchUpMin, { impulse().launchUpMin }, { v -> setImpulse { it.copy(launchUpMin = v) } }, 0.0..1.5)
                    addDouble("$GK.dive_launch_up_max", "$GK.dive_launch_up_max.desc", GK_DIVE_IMP.launchUpMax, { impulse().launchUpMax }, { v -> setImpulse { it.copy(launchUpMax = v) } }, 0.0..1.5)
                    addDouble("$GK.dive_sustain_fwd_min", "$GK.dive_sustain_fwd_min.desc", GK_DIVE_IMP.sustainForwardMinScale, { impulse().sustainForwardMinScale }, { v -> setImpulse { it.copy(sustainForwardMinScale = v) } }, 0.1..5.0)
                    addDouble("$GK.dive_sustain_fwd_max", "$GK.dive_sustain_fwd_max.desc", GK_DIVE_IMP.sustainForwardMaxScale, { impulse().sustainForwardMaxScale }, { v -> setImpulse { it.copy(sustainForwardMaxScale = v) } }, 0.1..6.0)
                }
                .build(),
        )
        .build()

    private fun particlesCategory(
        getter: () -> FootballServerConfig,
        setter: (FootballServerConfig) -> Unit,
    ): ConfigCategory = ConfigCategory.createBuilder()
        .name(Component.translatable("yacl3.config.$MOD_ID.server.category.particles"))
        .group(
            OptionGroup.createBuilder()
                .name(Component.translatable("yacl3.config.$MOD_ID.server.group.particles_counts"))
                .apply {
                    fun pt(): ParticleActionCounts = getter().particles.counts
                    fun setPt(t: (ParticleActionCounts) -> ParticleActionCounts) {
                        val p = getter().particles
                        setter(getter().copy(particles = p.copy(counts = t(pt()))))
                    }

                    addInt("$PT.kick_count_base", "$PT.kick_count_base.desc", PT_COUNTS.kickCountBase, { pt().kickCountBase }, { v -> setPt { it.copy(kickCountBase = v) } }, 0..64)
                    addInt("$PT.dribble_count", "$PT.dribble_count.desc", PT_COUNTS.dribbleCount, { pt().dribbleCount }, { v -> setPt { it.copy(dribbleCount = v) } }, 0..32)
                    addDouble("$PT.high_speed_drag_min", "$PT.high_speed_drag_min.desc", PT_DRAG.highSpeedDragMinSpeed, { getter().particles.highSpeedDrag.highSpeedDragMinSpeed }, { v ->
                        val p = getter().particles
                        setter(getter().copy(particles = p.copy(highSpeedDrag = p.highSpeedDrag.copy(highSpeedDragMinSpeed = v))))
                    }, 0.0..3.0)
                }
                .build(),
        )
        .build()

    private const val MOD_ID = NMBCTFootball.MOD_ID
    private const val P = "yacl3.config.$MOD_ID.server.physics"
    private const val PI = "yacl3.config.$MOD_ID.server.player"
    private const val GK = "yacl3.config.$MOD_ID.server.goalkeeper"
    private const val PT = "yacl3.config.$MOD_ID.server.particles"

    private val DEF = FootballServerConfig.DEFAULT
    private val PHY_CORE = DEF.physics.core
    private val PHY_COL = DEF.physics.collision
    private val PHY_KICK = DEF.physics.kick
    private val PL_KICK = DEF.playerInput.kick
    private val PL_CHARGE = DEF.playerInput.charge
    private val PL_DRIBBLE = DEF.playerInput.dribble
    private val GK_CATCH = DEF.goalkeeper.catch
    private val GK_DIVE_BEH = DEF.goalkeeper.dive.behavior
    private val GK_DIVE_PITCH = DEF.goalkeeper.dive.pitch
    private val GK_DIVE_IMP = DEF.goalkeeper.dive.impulse
    private val GK_DIVE_ACT = DEF.goalkeeper.dive.actions
    private val PT_COUNTS = DEF.particles.counts
    private val PT_DRAG = DEF.particles.highSpeedDrag
}
