package net.astrorbits.football.client.config.yacl

import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.OptionGroup
import dev.isxander.yacl3.api.YetAnotherConfigLib
import net.astrorbits.football.match.MatchConfig
import net.astrorbits.football.client.util.YaclOptionUtil.addBoolean
import net.astrorbits.football.client.util.YaclOptionUtil.addInt
import net.astrorbits.football.client.util.YaclOptionUtil.addString
import net.astrorbits.football.network.MatchConfigApplyC2SPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

object MatchSetupConfigScreen {
    fun create(parent: Screen?, initial: MatchConfig): Screen {
        var draft = initial
        val def = MatchConfig.DEFAULT

        return YetAnotherConfigLib.createBuilder()
            .title(Component.translatable("screen.nmbct-football.match.title"))
            .category(teamsCategory({ draft }, { draft = it }, def))
            .category(timingCategory({ draft }, { draft = it }, def))
            .category(extraCategory({ draft }, { draft = it }, def))
            .save {
                val saved = draft.copy(
                    halfTimeMinutes = draft.halfTimeMinutes.coerceAtLeast(1),
                    stoppageTimeMaxMinutes = draft.stoppageTimeMaxMinutes.coerceAtLeast(0),
                    extraTimeHalfMinutes = draft.extraTimeHalfMinutes.coerceAtLeast(1),
                    postGoalBallResetDelaySeconds = draft.postGoalBallResetDelaySeconds.coerceAtLeast(0),
                )
                if (ClientPlayNetworking.canSend(MatchConfigApplyC2SPayload.TYPE)) {
                    ClientPlayNetworking.send(MatchConfigApplyC2SPayload(saved))
                }
            }
            .build()
            .generateScreen(parent)
    }

    private fun teamsCategory(
        getter: () -> MatchConfig,
        setter: (MatchConfig) -> Unit,
        def: MatchConfig,
    ): ConfigCategory = ConfigCategory.createBuilder()
        .name(Component.translatable("screen.nmbct-football.match.tab.teams"))
        .group(
            setupGroup(
                "screen.nmbct-football.match.tab.teams",
                {
                    addString(
                        nameKey = "screen.nmbct-football.match.team_a_name",
                        descKey = MatchYaclDesc.desc("screen.nmbct-football.match.team_a_name"),
                        defaultValue = def.teamAName,
                        getter = { getter().teamAName },
                        setter = { v -> setter(getter().copy(teamAName = v)) },
                    )
                    addString(
                        nameKey = "screen.nmbct-football.match.team_b_name",
                        descKey = MatchYaclDesc.desc("screen.nmbct-football.match.team_b_name"),
                        defaultValue = def.teamBName,
                        getter = { getter().teamBName },
                        setter = { v -> setter(getter().copy(teamBName = v)) },
                    )
                },
            ),
        )
        .build()

    private fun timingCategory(
        getter: () -> MatchConfig,
        setter: (MatchConfig) -> Unit,
        def: MatchConfig,
    ): ConfigCategory = ConfigCategory.createBuilder()
        .name(Component.translatable("screen.nmbct-football.match.tab.timing"))
        .group(
            setupGroup(
                "screen.nmbct-football.match.tab.timing",
                {
                    addInt(
                        nameKey = "screen.nmbct-football.match.half_time_minutes",
                        descKey = MatchYaclDesc.desc("screen.nmbct-football.match.half_time_minutes"),
                        defaultValue = def.halfTimeMinutes,
                        getter = { getter().halfTimeMinutes },
                        setter = { v -> setter(getter().copy(halfTimeMinutes = v)) },
                        range = 1..120,
                    )
                    addBoolean(
                        nameKey = "screen.nmbct-football.match.enable_stoppage_time",
                        descKey = MatchYaclDesc.desc("screen.nmbct-football.match.enable_stoppage_time"),
                        defaultValue = def.enableStoppageTime,
                        getter = { getter().enableStoppageTime },
                        setter = { v -> setter(getter().copy(enableStoppageTime = v)) },
                    )
                    addInt(
                        nameKey = "screen.nmbct-football.match.stoppage_time_max",
                        descKey = MatchYaclDesc.desc("screen.nmbct-football.match.stoppage_time_max"),
                        defaultValue = def.stoppageTimeMaxMinutes,
                        getter = { getter().stoppageTimeMaxMinutes },
                        setter = { v -> setter(getter().copy(stoppageTimeMaxMinutes = v)) },
                        range = 0..30,
                    )
                    addInt(
                        nameKey = "screen.nmbct-football.match.post_goal_ball_reset_delay_seconds",
                        descKey = MatchYaclDesc.desc("screen.nmbct-football.match.post_goal_ball_reset_delay_seconds"),
                        defaultValue = def.postGoalBallResetDelaySeconds,
                        getter = { getter().postGoalBallResetDelaySeconds },
                        setter = { v -> setter(getter().copy(postGoalBallResetDelaySeconds = v)) },
                        range = 0..60,
                    )
                },
            ),
        )
        .build()

    private fun extraCategory(
        getter: () -> MatchConfig,
        setter: (MatchConfig) -> Unit,
        def: MatchConfig,
    ): ConfigCategory = ConfigCategory.createBuilder()
        .name(Component.translatable("screen.nmbct-football.match.tab.extra"))
        .group(
            setupGroup(
                "screen.nmbct-football.match.tab.extra",
                {
                    addBoolean(
                        nameKey = "screen.nmbct-football.match.enable_extra_time",
                        descKey = MatchYaclDesc.desc("screen.nmbct-football.match.enable_extra_time"),
                        defaultValue = def.enableExtraTime,
                        getter = { getter().enableExtraTime },
                        setter = { v -> setter(getter().copy(enableExtraTime = v)) },
                    )
                    addInt(
                        nameKey = "screen.nmbct-football.match.extra_time_half",
                        descKey = MatchYaclDesc.desc("screen.nmbct-football.match.extra_time_half"),
                        defaultValue = def.extraTimeHalfMinutes,
                        getter = { getter().extraTimeHalfMinutes },
                        setter = { v -> setter(getter().copy(extraTimeHalfMinutes = v)) },
                        range = 1..30,
                    )
                    addBoolean(
                        nameKey = "screen.nmbct-football.match.enable_penalty_shootout",
                        descKey = MatchYaclDesc.desc("screen.nmbct-football.match.enable_penalty_shootout"),
                        defaultValue = def.enablePenaltyShootout,
                        getter = { getter().enablePenaltyShootout },
                        setter = { v -> setter(getter().copy(enablePenaltyShootout = v)) },
                    )
                },
            ),
        )
        .build()

    private fun setupGroup(
        nameKey: String,
        block: OptionGroup.Builder.() -> Unit,
    ): OptionGroup = OptionGroup.createBuilder()
        .name(Component.translatable(nameKey))
        .description(OptionDescription.of(Component.translatable(MatchYaclDesc.desc(nameKey))))
        .apply(block)
        .build()
}
