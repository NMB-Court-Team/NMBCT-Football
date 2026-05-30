package net.astrorbits.football.match

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/** 球门：两个对角 3D 点定义的垂直矩形 + 球门朝向 */
data class GoalConfig(
    val x1: Double = 0.0,
    val y1: Double = 64.0,
    val z1: Double = 0.0,
    val x2: Double = 0.0,
    val y2: Double = 66.0,
    val z2: Double = 0.0,
    val facingX: Double = 0.0,
    val facingY: Double = 0.0,
    val facingZ: Double = 0.0,
) {
    companion object {
        val CODEC: Codec<GoalConfig> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.DOUBLE.fieldOf("x1").forGetter(GoalConfig::x1),
                Codec.DOUBLE.fieldOf("y1").forGetter(GoalConfig::y1),
                Codec.DOUBLE.fieldOf("z1").forGetter(GoalConfig::z1),
                Codec.DOUBLE.fieldOf("x2").forGetter(GoalConfig::x2),
                Codec.DOUBLE.fieldOf("y2").forGetter(GoalConfig::y2),
                Codec.DOUBLE.fieldOf("z2").forGetter(GoalConfig::z2),
                Codec.DOUBLE.fieldOf("facing_x").forGetter(GoalConfig::facingX),
                Codec.DOUBLE.fieldOf("facing_y").forGetter(GoalConfig::facingY),
                Codec.DOUBLE.fieldOf("facing_z").forGetter(GoalConfig::facingZ),
            ).apply(i, ::GoalConfig)
        }

        val DEFAULT = GoalConfig()
    }
}

data class MatchConfig(
    val teamAName: String = DEFAULT_TEAM_A_NAME,
    val teamBName: String = DEFAULT_TEAM_B_NAME,
    val halfTimeMinutes: Int = 5,
    val enableStoppageTime: Boolean = false,
    val stoppageTimeMaxMinutes: Int = 3,
    val enableExtraTime: Boolean = false,
    val extraTimeHalfMinutes: Int = 3,
    val enablePenaltyShootout: Boolean = false,
    val enableGoalDetection: Boolean = false,
    val goalA: GoalConfig = GoalConfig.DEFAULT,
    val goalB: GoalConfig = GoalConfig.DEFAULT,
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
                Codec.BOOL.fieldOf("enable_goal_detection").forGetter(MatchConfig::enableGoalDetection),
                GoalConfig.CODEC.fieldOf("goal_a").forGetter(MatchConfig::goalA),
                GoalConfig.CODEC.fieldOf("goal_b").forGetter(MatchConfig::goalB),
            ).apply(i, ::MatchConfig)
        }

        val DEFAULT = MatchConfig()
    }
}
