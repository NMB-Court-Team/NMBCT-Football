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

/** 开球/角球/门球放置点 */
data class KickPosition(
    val x: Double = 0.0,
    val y: Double = 64.0,
    val z: Double = 0.0,
) {
    companion object {
        val CODEC: Codec<KickPosition> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.DOUBLE.fieldOf("x").forGetter(KickPosition::x),
                Codec.DOUBLE.fieldOf("y").forGetter(KickPosition::y),
                Codec.DOUBLE.fieldOf("z").forGetter(KickPosition::z),
            ).apply(i, ::KickPosition)
        }

        val DEFAULT = KickPosition()
    }
}

/**
 * 边线：选 X 轴则坐标填 X 值，线沿 Z 延伸，场内方向沿 X 正/负。
 * 例：axis=X, coord=50, positiveInside=false → X=50 的线，X<50 是场内。
 */
data class SidelineConfig(
    val coord: Double = 0.0,
    val axis: String = "x",
    val positiveInside: Boolean = false,
) {
    companion object {
        val CODEC: Codec<SidelineConfig> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.DOUBLE.fieldOf("coord").forGetter(SidelineConfig::coord),
                Codec.STRING.fieldOf("axis").forGetter(SidelineConfig::axis),
                Codec.BOOL.fieldOf("positive_inside").forGetter(SidelineConfig::positiveInside),
            ).apply(i, ::SidelineConfig)
        }

        val DEFAULT = SidelineConfig()
    }

    /** 场内方向：positiveInside=true 时正方向是场内，facing 指向场内 */
    fun facing(): net.minecraft.world.phys.Vec3 {
        val sign = if (positiveInside) 1.0 else -1.0
        return when (axis.lowercase()) {
            "x" -> net.minecraft.world.phys.Vec3(sign, 0.0, 0.0)
            else -> net.minecraft.world.phys.Vec3(0.0, 0.0, sign)
        }
    }

    /** 边线上一点：坐标填哪个轴，就在那个轴上取 coord */
    fun origin(): net.minecraft.world.phys.Vec3 {
        return when (axis.lowercase()) {
            "x" -> net.minecraft.world.phys.Vec3(coord, 0.0, 0.0)
            else -> net.minecraft.world.phys.Vec3(0.0, 0.0, coord)
        }
    }
}

/** 球门：两个对角 3D 点定义的垂直矩形 + 球门朝向 + 角球点/门球点 */
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
    val goalKick: KickPosition = KickPosition.DEFAULT,
    val cornerKickLeft: KickPosition = KickPosition.DEFAULT,
    val cornerKickRight: KickPosition = KickPosition.DEFAULT,
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
                KickPosition.CODEC.optionalFieldOf("goal_kick", KickPosition.DEFAULT).forGetter(GoalConfig::goalKick),
                KickPosition.CODEC.optionalFieldOf("corner_kick_left", KickPosition.DEFAULT).forGetter(GoalConfig::cornerKickLeft),
                KickPosition.CODEC.optionalFieldOf("corner_kick_right", KickPosition.DEFAULT).forGetter(GoalConfig::cornerKickRight),
            ).apply(i, ::GoalConfig)
        }

        val DEFAULT = GoalConfig()
    }
}

data class MatchConfig(
    val teamAName: String = DEFAULT_TEAM_A_NAME,
    val teamBName: String = DEFAULT_TEAM_B_NAME,
    val rules: MatchRulesSettings = MatchRulesSettings.DEFAULT,
    val accessibility: MatchAccessibilitySettings = MatchAccessibilitySettings.DEFAULT,
    val goalA: GoalConfig = GoalConfig.DEFAULT,
    val goalB: GoalConfig = GoalConfig.DEFAULT,
    val sidelineA: SidelineConfig = SidelineConfig.DEFAULT,
    val sidelineB: SidelineConfig = SidelineConfig.DEFAULT,
    val kickOff: KickPosition = KickPosition(8.5, -60.0, 8.5),
    val teamASpawn: TeamSpawnConfig = TeamSpawnConfig.DEFAULT,
    val teamBSpawn: TeamSpawnConfig = TeamSpawnConfig.DEFAULT,
) {
    val halfTimeMinutes: Int get() = rules.halfTimeMinutes
    val enableStoppageTime: Boolean get() = rules.enableStoppageTime
    val stoppageTimeMaxMinutes: Int get() = rules.stoppageTimeMaxMinutes
    val enableExtraTime: Boolean get() = rules.enableExtraTime
    val extraTimeHalfMinutes: Int get() = rules.extraTimeHalfMinutes
    val enablePenaltyShootout: Boolean get() = rules.enablePenaltyShootout
    val postGoalBallResetDelaySeconds: Int get() = rules.postGoalBallResetDelaySeconds

    companion object {
        const val DEFAULT_TEAM_A_NAME = "红队"
        const val DEFAULT_TEAM_B_NAME = "蓝队"

        private val NESTED_CODEC: Codec<MatchConfig> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.STRING.fieldOf("team_a_name").forGetter(MatchConfig::teamAName),
                Codec.STRING.fieldOf("team_b_name").forGetter(MatchConfig::teamBName),
                MatchRulesSettings.CODEC.optionalFieldOf("rules", MatchRulesSettings.DEFAULT).forGetter(MatchConfig::rules),
                MatchAccessibilitySettings.CODEC.optionalFieldOf("accessibility", MatchAccessibilitySettings.DEFAULT)
                    .forGetter(MatchConfig::accessibility),
                GoalConfig.CODEC.optionalFieldOf("goal_a", GoalConfig.DEFAULT).forGetter(MatchConfig::goalA),
                GoalConfig.CODEC.optionalFieldOf("goal_b", GoalConfig.DEFAULT).forGetter(MatchConfig::goalB),
                SidelineConfig.CODEC.optionalFieldOf("sideline_a", SidelineConfig.DEFAULT).forGetter(MatchConfig::sidelineA),
                SidelineConfig.CODEC.optionalFieldOf("sideline_b", SidelineConfig.DEFAULT).forGetter(MatchConfig::sidelineB),
                KickPosition.CODEC.optionalFieldOf("kick_off", KickPosition(8.5, -60.0, 8.5)).forGetter(MatchConfig::kickOff),
                TeamSpawnConfig.CODEC.optionalFieldOf("team_a_spawn", TeamSpawnConfig.DEFAULT).forGetter(MatchConfig::teamASpawn),
                TeamSpawnConfig.CODEC.optionalFieldOf("team_b_spawn", TeamSpawnConfig.DEFAULT).forGetter(MatchConfig::teamBSpawn),
            ).apply(i, ::MatchConfig)
        }

        /** 兼容根级 `half_time_minutes` 等字段的旧版 JSON。 */
        private val FLAT_LEGACY_CODEC: Codec<MatchConfig> = RecordCodecBuilder.create { i ->
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
                SidelineConfig.CODEC.optionalFieldOf("sideline_a", SidelineConfig.DEFAULT).forGetter(MatchConfig::sidelineA),
                SidelineConfig.CODEC.optionalFieldOf("sideline_b", SidelineConfig.DEFAULT).forGetter(MatchConfig::sidelineB),
                KickPosition.CODEC.optionalFieldOf("kick_off", KickPosition(8.5, -60.0, 8.5)).forGetter(MatchConfig::kickOff),
                Codec.INT.optionalFieldOf("post_goal_ball_reset_delay_seconds", 3)
                    .forGetter(MatchConfig::postGoalBallResetDelaySeconds),
                TeamSpawnConfig.CODEC.optionalFieldOf("team_a_spawn", TeamSpawnConfig.DEFAULT).forGetter(MatchConfig::teamASpawn),
                TeamSpawnConfig.CODEC.optionalFieldOf("team_b_spawn", TeamSpawnConfig.DEFAULT).forGetter(MatchConfig::teamBSpawn),
            ).apply(i) { teamAName, teamBName, halfTime, stoppage, stoppageMax, extra, extraHalf, penalty, goalA, goalB, sidelineA, sidelineB, kickOff, postGoal, teamASpawn, teamBSpawn ->
                MatchConfig(
                    teamAName = teamAName,
                    teamBName = teamBName,
                    rules = MatchRulesSettings(
                        halfTimeMinutes = halfTime,
                        enableStoppageTime = stoppage,
                        stoppageTimeMaxMinutes = stoppageMax,
                        enableExtraTime = extra,
                        extraTimeHalfMinutes = extraHalf,
                        enablePenaltyShootout = penalty,
                        postGoalBallResetDelaySeconds = postGoal,
                    ),
                    goalA = goalA,
                    goalB = goalB,
                    sidelineA = sidelineA,
                    sidelineB = sidelineB,
                    kickOff = kickOff,
                    teamASpawn = teamASpawn,
                    teamBSpawn = teamBSpawn,
                )
            }
        }

        val CODEC: Codec<MatchConfig> = Codec.withAlternative(NESTED_CODEC, FLAT_LEGACY_CODEC)

        val DEFAULT = MatchConfig()
    }
}
