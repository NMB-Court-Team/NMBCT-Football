package net.astrorbits.football.config.server

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder

/** 滑铲位移、冷却与碰撞（服务端权威）；体力消耗见 [StaminaActionCostsSettings]。 */
data class PlayerSlideTackleSettings(
    val cooldownSeconds: Float = 3f,
    val minSlideTicks: Int = 8,
    val initialSpeed: Double = 1.05,
    val initialHoldTicks: Int = 6,
    val decayTicks: Int = 14,
    val endSpeedRetain: Double = 0.85,
    val minSprintTicks: Int = 5,
    val tacklerSpeedDampOnContact: Double = 0.85,
    val contactDistancePenaltyTicks: Int = 5,
    val victimPushSpeed: Double = 1.1,
    val victimKnockbackUpward: Double = 0.28,
    val victimResistanceTicks: Int = 12,
    val victimResistanceFactor: Double = 0.35,
    val victimJumpBlockTicks: Int = 14,
    val ballContactGraceTicks: Int = 14,
    val ballKickForce: Double = 3.0,
) {
    val cooldownTicks: Int
        get() = (cooldownSeconds * TICKS_PER_SECOND).toInt()

    companion object {
        const val TICKS_PER_SECOND = 20

        val CODEC: Codec<PlayerSlideTackleSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.FLOAT.optionalFieldOf("slide_tackle_cooldown_seconds", 3f).forGetter(PlayerSlideTackleSettings::cooldownSeconds),
                Codec.INT.optionalFieldOf("min_slide_ticks", 8).forGetter(PlayerSlideTackleSettings::minSlideTicks),
                Codec.DOUBLE.optionalFieldOf("slide_initial_speed", 1.05).forGetter(PlayerSlideTackleSettings::initialSpeed),
                Codec.INT.optionalFieldOf("slide_initial_hold_ticks", 6).forGetter(PlayerSlideTackleSettings::initialHoldTicks),
                Codec.INT.optionalFieldOf("slide_decay_ticks", 14).forGetter(PlayerSlideTackleSettings::decayTicks),
                Codec.DOUBLE.optionalFieldOf("slide_end_speed_retain", 0.85).forGetter(PlayerSlideTackleSettings::endSpeedRetain),
                Codec.INT.optionalFieldOf("slide_min_sprint_ticks", 5).forGetter(PlayerSlideTackleSettings::minSprintTicks),
                Codec.DOUBLE.optionalFieldOf("slide_tackler_speed_damp_on_contact", 0.85)
                    .forGetter(PlayerSlideTackleSettings::tacklerSpeedDampOnContact),
                Codec.INT.optionalFieldOf("slide_contact_distance_penalty_ticks", 5)
                    .forGetter(PlayerSlideTackleSettings::contactDistancePenaltyTicks),
                Codec.DOUBLE.optionalFieldOf("slide_victim_push_speed", 1.1).forGetter(PlayerSlideTackleSettings::victimPushSpeed),
                Codec.DOUBLE.optionalFieldOf("slide_victim_knockback_upward", 0.28)
                    .forGetter(PlayerSlideTackleSettings::victimKnockbackUpward),
                Codec.INT.optionalFieldOf("slide_victim_resistance_ticks", 12).forGetter(PlayerSlideTackleSettings::victimResistanceTicks),
                Codec.DOUBLE.optionalFieldOf("slide_victim_resistance_factor", 0.35)
                    .forGetter(PlayerSlideTackleSettings::victimResistanceFactor),
                Codec.INT.optionalFieldOf("slide_victim_jump_block_ticks", 14).forGetter(PlayerSlideTackleSettings::victimJumpBlockTicks),
                Codec.INT.optionalFieldOf("slide_ball_contact_grace_ticks", 14).forGetter(PlayerSlideTackleSettings::ballContactGraceTicks),
                Codec.DOUBLE.optionalFieldOf("slide_ball_kick_force", 3.0).forGetter(PlayerSlideTackleSettings::ballKickForce),
            ).apply(i, ::PlayerSlideTackleSettings)
        }.validate(::validate)

        val DEFAULT = PlayerSlideTackleSettings()

        fun validate(settings: PlayerSlideTackleSettings): DataResult<PlayerSlideTackleSettings> {
            if (settings.cooldownSeconds !in 0f..30f) {
                return DataResult.error { "slide_tackle_cooldown_seconds must be in [0, 30], was ${settings.cooldownSeconds}" }
            }
            if (settings.minSlideTicks !in 1..60) {
                return DataResult.error { "min_slide_ticks must be in [1, 60], was ${settings.minSlideTicks}" }
            }
            if (settings.initialSpeed !in 0.1..5.0) {
                return DataResult.error { "slide_initial_speed must be in [0.1, 5], was ${settings.initialSpeed}" }
            }
            if (settings.initialHoldTicks !in 0..40) {
                return DataResult.error { "slide_initial_hold_ticks must be in [0, 40], was ${settings.initialHoldTicks}" }
            }
            if (settings.decayTicks !in 1..60) {
                return DataResult.error { "slide_decay_ticks must be in [1, 60], was ${settings.decayTicks}" }
            }
            if (settings.endSpeedRetain !in 0.0..1.0) {
                return DataResult.error { "slide_end_speed_retain must be in [0, 1], was ${settings.endSpeedRetain}" }
            }
            if (settings.minSprintTicks !in 0..40) {
                return DataResult.error { "slide_min_sprint_ticks must be in [0, 40], was ${settings.minSprintTicks}" }
            }
            if (settings.tacklerSpeedDampOnContact !in 0.0..1.0) {
                return DataResult.error {
                    "slide_tackler_speed_damp_on_contact must be in [0, 1], was ${settings.tacklerSpeedDampOnContact}"
                }
            }
            if (settings.contactDistancePenaltyTicks !in 0..40) {
                return DataResult.error {
                    "slide_contact_distance_penalty_ticks must be in [0, 40], was ${settings.contactDistancePenaltyTicks}"
                }
            }
            if (settings.victimPushSpeed !in 0.0..3.0) {
                return DataResult.error { "slide_victim_push_speed must be in [0, 3], was ${settings.victimPushSpeed}" }
            }
            if (settings.victimKnockbackUpward !in 0.0..1.5) {
                return DataResult.error { "slide_victim_knockback_upward must be in [0, 1.5], was ${settings.victimKnockbackUpward}" }
            }
            if (settings.victimResistanceTicks !in 0..120) {
                return DataResult.error { "slide_victim_resistance_ticks must be in [0, 120], was ${settings.victimResistanceTicks}" }
            }
            if (settings.victimResistanceFactor !in 0.0..1.0) {
                return DataResult.error { "slide_victim_resistance_factor must be in [0, 1], was ${settings.victimResistanceFactor}" }
            }
            if (settings.victimJumpBlockTicks !in 0..120) {
                return DataResult.error { "slide_victim_jump_block_ticks must be in [0, 120], was ${settings.victimJumpBlockTicks}" }
            }
            if (settings.ballContactGraceTicks !in 0..60) {
                return DataResult.error { "slide_ball_contact_grace_ticks must be in [0, 60], was ${settings.ballContactGraceTicks}" }
            }
            if (settings.ballKickForce !in 0.1..10.0) {
                return DataResult.error { "slide_ball_kick_force must be in [0.1, 10], was ${settings.ballKickForce}" }
            }
            return DataResult.success(settings)
        }
    }
}
