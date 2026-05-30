package net.astrorbits.football.match

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/** 出生点坐标 */
data class SpawnPosition(
    val x: Double = 0.0,
    val y: Double = 64.0,
    val z: Double = 0.0,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
) {
    companion object {
        val CODEC: Codec<SpawnPosition> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.DOUBLE.fieldOf("x").forGetter(SpawnPosition::x),
                Codec.DOUBLE.fieldOf("y").forGetter(SpawnPosition::y),
                Codec.DOUBLE.fieldOf("z").forGetter(SpawnPosition::z),
                Codec.FLOAT.optionalFieldOf("yaw", 0f).forGetter(SpawnPosition::yaw),
                Codec.FLOAT.optionalFieldOf("pitch", 0f).forGetter(SpawnPosition::pitch),
            ).apply(i, ::SpawnPosition)
        }

        val DEFAULT = SpawnPosition()
    }
}

/** 单支队伍的出生点配置：守门员位置 + 普通队员位置列表 */
data class TeamSpawnConfig(
    val gk: SpawnPosition = SpawnPosition.DEFAULT,
    val players: List<SpawnPosition> = emptyList(),
) {
    companion object {
        private val SPAWN_POSITION_LIST: Codec<List<SpawnPosition>> =
            SpawnPosition.CODEC.listOf()

        val CODEC: Codec<TeamSpawnConfig> = RecordCodecBuilder.create { i ->
            i.group(
                SpawnPosition.CODEC.optionalFieldOf("gk", SpawnPosition.DEFAULT).forGetter(TeamSpawnConfig::gk),
                SPAWN_POSITION_LIST.optionalFieldOf("players", emptyList<SpawnPosition>()).forGetter(TeamSpawnConfig::players),
            ).apply(i, ::TeamSpawnConfig)
        }

        val DEFAULT = TeamSpawnConfig()
    }
}

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
    val goalA: GoalConfig = GoalConfig.DEFAULT,
    val goalB: GoalConfig = GoalConfig.DEFAULT,
    val teamASpawn: TeamSpawnConfig = TeamSpawnConfig.DEFAULT,
    val teamBSpawn: TeamSpawnConfig = TeamSpawnConfig.DEFAULT,
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
                GoalConfig.CODEC.optionalFieldOf("goal_a", GoalConfig.DEFAULT).forGetter(MatchConfig::goalA),
                GoalConfig.CODEC.optionalFieldOf("goal_b", GoalConfig.DEFAULT).forGetter(MatchConfig::goalB),
                TeamSpawnConfig.CODEC.optionalFieldOf("team_a_spawn", TeamSpawnConfig.DEFAULT).forGetter(MatchConfig::teamASpawn),
                TeamSpawnConfig.CODEC.optionalFieldOf("team_b_spawn", TeamSpawnConfig.DEFAULT).forGetter(MatchConfig::teamBSpawn),
            ).apply(i, ::MatchConfig)
        }

        val DEFAULT = MatchConfig()
    }
}
