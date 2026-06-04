package net.astrorbits.football.match

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class MatchRulesSettings(
    val halfTimeMinutes: Int = 5,
    val enableStoppageTime: Boolean = false,
    val stoppageTimeMaxMinutes: Int = 3,
    val enableExtraTime: Boolean = false,
    val extraTimeHalfMinutes: Int = 3,
    val enablePenaltyShootout: Boolean = false,
    val postGoalBallResetDelaySeconds: Int = 3,
) {
    private fun skipsRegularAndExtraTime(): Boolean =
        halfTimeMinutes == 0 && (!enableExtraTime || extraTimeHalfMinutes == 0)

    /** 无常规/加时，开赛即点球大战（测试用）。 */
    fun startsWithPenaltyShootout(): Boolean =
        enablePenaltyShootout && skipsRegularAndExtraTime()

    /** 无常规、无加时、也未启用点球，无法开赛。 */
    fun hasNoPlayableDuration(): Boolean =
        skipsRegularAndExtraTime() && !enablePenaltyShootout

    companion object {
        val CODEC: Codec<MatchRulesSettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.INT.fieldOf("half_time_minutes").forGetter(MatchRulesSettings::halfTimeMinutes),
                Codec.BOOL.fieldOf("enable_stoppage_time").forGetter(MatchRulesSettings::enableStoppageTime),
                Codec.INT.fieldOf("stoppage_time_max_minutes").forGetter(MatchRulesSettings::stoppageTimeMaxMinutes),
                Codec.BOOL.fieldOf("enable_extra_time").forGetter(MatchRulesSettings::enableExtraTime),
                Codec.INT.fieldOf("extra_time_half_minutes").forGetter(MatchRulesSettings::extraTimeHalfMinutes),
                Codec.BOOL.fieldOf("enable_penalty_shootout").forGetter(MatchRulesSettings::enablePenaltyShootout),
                Codec.INT.optionalFieldOf("post_goal_ball_reset_delay_seconds", 3)
                    .forGetter(MatchRulesSettings::postGoalBallResetDelaySeconds),
            ).apply(i, ::MatchRulesSettings)
        }

        val DEFAULT = MatchRulesSettings()
    }
}
