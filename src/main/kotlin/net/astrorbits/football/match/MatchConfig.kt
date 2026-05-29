package net.astrorbits.football.match

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class MatchConfig(
    val teamAName: String = DEFAULT_TEAM_A_NAME,
    val teamBName: String = DEFAULT_TEAM_B_NAME,
    val halfTimeMinutes: Int = 5,
    val enableStoppageTime: Boolean = false,
    val stoppageTimeMaxMinutes: Int = 3,
    val enableExtraTime: Boolean = false,
    val extraTimeHalfMinutes: Int = 3,
    val enablePenaltyShootout: Boolean = false,
) {
    companion object {
        const val DEFAULT_TEAM_A_NAME = "红队"
        const val DEFAULT_TEAM_B_NAME = "蓝队"

        val CODEC: Codec<MatchConfig> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.STRING.fieldOf("team_a_name").forGetter(MatchConfig::teamAName),
                Codec.STRING.fieldOf("team_b_name").forGetter(MatchConfig::teamBName),
                Codec.INT.fieldOf("half_time_minutes").forGetter(MatchConfig::halfTimeMinutes),
                Codec.BOOL.fieldOf("enable_stoppage_time").forGetter(MatchConfig::enableStoppageTime),
                Codec.INT.fieldOf("stoppage_time_max_minutes").forGetter(MatchConfig::stoppageTimeMaxMinutes),
                Codec.BOOL.fieldOf("enable_extra_time").forGetter(MatchConfig::enableExtraTime),
                Codec.INT.fieldOf("extra_time_half_minutes").forGetter(MatchConfig::extraTimeHalfMinutes),
                Codec.BOOL.fieldOf("enable_penalty_shootout").forGetter(MatchConfig::enablePenaltyShootout),
            ).apply(i, ::MatchConfig)
        }

        val DEFAULT = MatchConfig()
    }
}
