package net.astrorbits.football.match

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.*

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
 * 边线：`axis` 为边线延伸方向（平行于该轴的触界线）。
 * - axis=x：边线沿 X，coord 为 Z，场内方向沿 Z 正/负。
 * - axis=z：边线沿 Z，coord 为 X，场内方向沿 X 正/负。
 */
data class SidelineConfig(
    val coord: Double = 0.0,
    val axis: String = "z",
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

    /** 将旧版「axis=边线法向轴」配置翻转为新版「axis=延伸方向」。 */
    fun flipLegacyAxis(): SidelineConfig = copy(
        axis = when (axis.lowercase()) {
            "x" -> "z"
            "z" -> "x"
            else -> axis
        },
    )

    /** 场内方向：positiveInside=true 时正方向是场内，facing 指向场内 */
    fun facing(): net.minecraft.world.phys.Vec3 {
        val sign = if (positiveInside) 1.0 else -1.0
        return when (axis.lowercase()) {
            "x" -> net.minecraft.world.phys.Vec3(0.0, 0.0, sign)
            else -> net.minecraft.world.phys.Vec3(sign, 0.0, 0.0)
        }
    }

    /** 边线上一点：延伸轴为 X 时取 Z=coord，延伸轴为 Z 时取 X=coord */
    fun origin(): net.minecraft.world.phys.Vec3 {
        return when (axis.lowercase()) {
            "x" -> net.minecraft.world.phys.Vec3(0.0, 0.0, coord)
            else -> net.minecraft.world.phys.Vec3(coord, 0.0, 0.0)
        }
    }
}

/** 半场区域：球门区（小禁区）、罚球区（大禁区）与罚球弧。 */
data class HalfAreaConfig(
    val goalAreaCorner1: KickPosition = KickPosition.DEFAULT,
    val goalAreaCorner2: KickPosition = KickPosition.DEFAULT,
    val penaltyAreaCorner1: KickPosition = KickPosition.DEFAULT,
    val penaltyAreaCorner2: KickPosition = KickPosition.DEFAULT,
    val penaltyArcRadius: Double = 10.0,
) {
    companion object {
        val CODEC: Codec<HalfAreaConfig> = RecordCodecBuilder.create { i ->
            i.group(
                KickPosition.CODEC.optionalFieldOf("goal_area_corner1", KickPosition.DEFAULT)
                    .forGetter(HalfAreaConfig::goalAreaCorner1),
                KickPosition.CODEC.optionalFieldOf("goal_area_corner2", KickPosition.DEFAULT)
                    .forGetter(HalfAreaConfig::goalAreaCorner2),
                KickPosition.CODEC.optionalFieldOf("penalty_area_corner1", KickPosition.DEFAULT)
                    .forGetter(HalfAreaConfig::penaltyAreaCorner1),
                KickPosition.CODEC.optionalFieldOf("penalty_area_corner2", KickPosition.DEFAULT)
                    .forGetter(HalfAreaConfig::penaltyAreaCorner2),
                Codec.DOUBLE.optionalFieldOf("penalty_arc_radius", 10.0).forGetter(HalfAreaConfig::penaltyArcRadius),
            ).apply(i, ::HalfAreaConfig)
        }

        val DEFAULT = HalfAreaConfig()
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
    /** 点球点；缺省时由门线沿 facing 反方向 11 格推导。 */
    val penaltySpot: KickPosition? = null,
    val halfArea: HalfAreaConfig = HalfAreaConfig.DEFAULT,
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
                KickPosition.CODEC.optionalFieldOf("penalty_spot")
                    .forGetter { Optional.ofNullable(it.penaltySpot) },
                HalfAreaConfig.CODEC.optionalFieldOf("half_area", HalfAreaConfig.DEFAULT).forGetter(GoalConfig::halfArea),
            ).apply(i) { x1, y1, z1, x2, y2, z2, fx, fy, fz, gk, cl, cr, pen, half ->
                GoalConfig(x1, y1, z1, x2, y2, z2, fx, fy, fz, gk, cl, cr, pen.orElse(null), half)
            }
        }

        val DEFAULT = GoalConfig()
    }
}

data class MatchConfig(
    val fieldConfigVersion: Int = FIELD_CONFIG_VERSION,
    val teamAName: String = DEFAULT_TEAM_A_NAME,
    val teamBName: String = DEFAULT_TEAM_B_NAME,
    val rules: MatchRulesSettings = MatchRulesSettings.DEFAULT,
    val accessibility: MatchAccessibilitySettings = MatchAccessibilitySettings.DEFAULT,
    val goalA: GoalConfig = GoalConfig.DEFAULT,
    val goalB: GoalConfig = GoalConfig.DEFAULT,
    val sidelineA: SidelineConfig = SidelineConfig.DEFAULT,
    val sidelineB: SidelineConfig = SidelineConfig.DEFAULT,
    val kickOff: KickPosition = KickPosition(8.5, -60.0, 8.5),
    val centerCircleRadius: Double = 10.0,
    val cornerKickPenaltyAreaRadius: Double = 10.0,
    val throwInPenaltyAreaRadius: Double = 2.5,
    val freeKickDistanceRadius: Double = 10.0,
    val teamASpawn: TeamSpawnConfig = TeamSpawnConfig.DEFAULT,
    val teamBSpawn: TeamSpawnConfig = TeamSpawnConfig.DEFAULT,
) {
    val halfTimeMinutes: Int get() = rules.halfTimeMinutes
    val enableOffside: Boolean get() = rules.enableOffside
    val enableSecondTouch: Boolean get() = rules.enableSecondTouch
    val enableStoppageTime: Boolean get() = rules.enableStoppageTime
    val stoppageTimeMaxMinutes: Int get() = rules.stoppageTimeMaxMinutes
    val enableExtraTime: Boolean get() = rules.enableExtraTime
    val extraTimeHalfMinutes: Int get() = rules.extraTimeHalfMinutes
    val enablePenaltyShootout: Boolean get() = rules.enablePenaltyShootout
    val postGoalBallResetDelaySeconds: Int get() = rules.postGoalBallResetDelaySeconds
    val setPieceStoppageAccumGraceSeconds: Int get() = rules.setPieceStoppageAccumGraceSeconds
    val sendOffDurationSeconds: Int get() = rules.sendOffDurationSeconds
    val enablePreMatchPreparation: Boolean get() = rules.enablePreMatchPreparation
    val preMatchPreparationMinutes: Int get() = rules.preMatchPreparationMinutes

    fun isPreMatchPreparationEnabled(): Boolean = rules.isPreMatchPreparationEnabled()

    fun startsWithPenaltyShootout(): Boolean = rules.startsWithPenaltyShootout()

    fun hasNoPlayableDuration(): Boolean = rules.hasNoPlayableDuration()

    companion object {
        const val DEFAULT_TEAM_A_NAME = "红队"
        const val DEFAULT_TEAM_B_NAME = "蓝队"
        const val LEGACY_FIELD_CONFIG_VERSION = 1
        const val FIELD_CONFIG_VERSION = 2

        private val NESTED_CODEC: Codec<MatchConfig> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.INT.optionalFieldOf("field_config_version", LEGACY_FIELD_CONFIG_VERSION)
                    .forGetter(MatchConfig::fieldConfigVersion),
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
                Codec.DOUBLE.optionalFieldOf("center_circle_radius", 10.0).forGetter(MatchConfig::centerCircleRadius),
                Codec.DOUBLE.optionalFieldOf("corner_kick_penalty_area_radius", 10.0).forGetter(MatchConfig::cornerKickPenaltyAreaRadius),
                Codec.DOUBLE.optionalFieldOf("throw_in_penalty_area_radius", 2.5).forGetter(MatchConfig::throwInPenaltyAreaRadius),
                Codec.DOUBLE.optionalFieldOf("free_kick_distance_radius", 10.0).forGetter(MatchConfig::freeKickDistanceRadius),
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
            ).apply(i, MatchConfig::fromFlatLegacyFields)
        }

        private fun fromFlatLegacyFields(
            teamAName: String,
            teamBName: String,
            halfTime: Int,
            stoppage: Boolean,
            stoppageMax: Int,
            extra: Boolean,
            extraHalf: Int,
            penalty: Boolean,
            goalA: GoalConfig,
            goalB: GoalConfig,
            sidelineA: SidelineConfig,
            sidelineB: SidelineConfig,
            kickOff: KickPosition,
            postGoal: Int,
            teamASpawn: TeamSpawnConfig,
            teamBSpawn: TeamSpawnConfig,
        ): MatchConfig = MatchConfig(
            fieldConfigVersion = LEGACY_FIELD_CONFIG_VERSION,
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

        val CODEC: Codec<MatchConfig> = Codec.withAlternative(NESTED_CODEC, FLAT_LEGACY_CODEC)

        val DEFAULT = MatchConfig(fieldConfigVersion = FIELD_CONFIG_VERSION)

        fun migrateFieldConfigIfNeeded(config: MatchConfig): MatchConfig {
            if (config.fieldConfigVersion >= FIELD_CONFIG_VERSION) {
                return config
            }
            return config.copy(
                fieldConfigVersion = FIELD_CONFIG_VERSION,
                sidelineA = config.sidelineA.flipLegacyAxis(),
                sidelineB = config.sidelineB.flipLegacyAxis(),
            )
        }
    }
}
