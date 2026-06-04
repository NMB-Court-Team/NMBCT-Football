package net.astrorbits.football.client.config.yacl

import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.ListOption
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.OptionGroup
import dev.isxander.yacl3.api.YetAnotherConfigLib
import net.astrorbits.football.client.config.yacl.controller.PositionAndFacing
import net.astrorbits.football.client.config.yacl.controller.PositionAndFacingController
import net.astrorbits.football.client.util.YaclOptionUtil.addBoolean
import net.astrorbits.football.client.util.YaclOptionUtil.addEnum
import net.astrorbits.football.match.GoalConfig
import net.astrorbits.football.match.KickPosition
import net.astrorbits.football.match.MatchConfig
import net.astrorbits.football.match.SidelineConfig
import net.astrorbits.football.match.SpawnPosition
import net.astrorbits.football.match.TeamSpawnConfig
import net.astrorbits.football.network.MatchConfigApplyC2SPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3

object MatchFieldConfigScreen {
    fun create(parent: Screen?, initial: MatchConfig): Screen {
        val ctx = MatchFieldDraftContext(initial)

        return YetAnotherConfigLib.createBuilder()
            .title(Component.translatable("screen.nmbct-football.field.title"))
            .category(goalCategory(ctx, "screen.nmbct-football.field.tab.goal_a", { ctx.draft.goalA }, { g -> ctx.draft = ctx.draft.copy(goalA = g) }))
            .category(goalCategory(ctx, "screen.nmbct-football.field.tab.goal_b", { ctx.draft.goalB }, { g -> ctx.draft = ctx.draft.copy(goalB = g) }))
            .category(spawnCategory(
                ctx,
                "screen.nmbct-football.field.tab.spawn_a",
                initial.teamASpawn.players,
                { ctx.draft.teamASpawn },
                { s -> ctx.draft = ctx.draft.copy(teamASpawn = s) },
            ))
            .category(spawnCategory(
                ctx,
                "screen.nmbct-football.field.tab.spawn_b",
                initial.teamBSpawn.players,
                { ctx.draft.teamBSpawn },
                { s -> ctx.draft = ctx.draft.copy(teamBSpawn = s) },
            ))
            .category(kickOffCategory(ctx))
            .category(sidelineCategory(
                ctx,
                "screen.nmbct-football.field.tab.sideline_a",
                { ctx.draft.sidelineA },
                { s -> ctx.draft = ctx.draft.copy(sidelineA = s) },
            ))
            .category(sidelineCategory(
                ctx,
                "screen.nmbct-football.field.tab.sideline_b",
                { ctx.draft.sidelineB },
                { s -> ctx.draft = ctx.draft.copy(sidelineB = s) },
            ))
            .save {
                if (ClientPlayNetworking.canSend(MatchConfigApplyC2SPayload.TYPE)) {
                    ClientPlayNetworking.send(MatchConfigApplyC2SPayload(ctx.draft))
                }
            }
            .build()
            .generateScreen(parent)
    }

    private fun goalCategory(
        ctx: MatchFieldDraftContext,
        tabKey: String,
        getter: () -> GoalConfig,
        setter: (GoalConfig) -> Unit,
    ): ConfigCategory {
        val side = if (tabKey.contains("goal_a")) "goal_a" else "goal_b"
        return ConfigCategory.createBuilder()
            .name(Component.translatable(tabKey))
            .group(
                OptionGroup.createBuilder()
                    .name(Component.translatable("yacl3.config.nmbct-football.match.field.goal_geometry"))
                    .description(
                        OptionDescription.of(
                            Component.translatable("yacl3.config.nmbct-football.match.field.goal_geometry.desc"),
                        ),
                    )
                    .apply {
                        addGoalGeometry(ctx, side, getter, setter)
                    }
                    .build(),
            )
            .group(kickPointGroup(ctx, side, "screen.nmbct-football.field.gk_kick_header", getter, setter, { it.goalKick }, { g, k -> g.copy(goalKick = k) }))
            .group(kickPointGroup(ctx, side, "screen.nmbct-football.field.penalty_spot_header", getter, setter, { g -> g.penaltySpot ?: KickPosition.DEFAULT }, { g, k -> g.copy(penaltySpot = k) }))
            .group(kickPointGroup(ctx, side, "screen.nmbct-football.field.cl_kick_header", getter, setter, { it.cornerKickLeft }, { g, k -> g.copy(cornerKickLeft = k) }))
            .group(kickPointGroup(ctx, side, "screen.nmbct-football.field.cr_kick_header", getter, setter, { it.cornerKickRight }, { g, k -> g.copy(cornerKickRight = k) }))
            .build()
    }

    private fun OptionGroup.Builder.addGoalGeometry(
        ctx: MatchFieldDraftContext,
        side: String,
        getter: () -> GoalConfig,
        setter: (GoalConfig) -> Unit,
    ) {
        fun g() = getter()
        addPosition(
            nameKey = "screen.nmbct-football.field.$side.corner1",
            descKey = MatchYaclDesc.desc("screen.nmbct-football.field.$side.corner1"),
            getter = { Vec3(g().x1, g().y1, g().z1) },
            setter = { v -> setter(g().copy(x1 = v.x, y1 = v.y, z1 = v.z)) },
            ctx = ctx,
        )
        addPosition(
            nameKey = "screen.nmbct-football.field.$side.corner2",
            descKey = MatchYaclDesc.desc("screen.nmbct-football.field.$side.corner2"),
            getter = { Vec3(g().x2, g().y2, g().z2) },
            setter = { v -> setter(g().copy(x2 = v.x, y2 = v.y, z2 = v.z)) },
            ctx = ctx,
        )
        addPosition(
            nameKey = "screen.nmbct-football.field.$side.facing",
            descKey = MatchYaclDesc.desc("screen.nmbct-football.field.$side.facing"),
            getter = { Vec3(g().facingX, g().facingY, g().facingZ) },
            setter = { v -> setter(g().copy(facingX = v.x, facingY = v.y, facingZ = v.z)) },
            showSampleButton = false,
            ctx = ctx,
        )
    }

    private fun kickPointGroup(
        ctx: MatchFieldDraftContext,
        side: String,
        headerKey: String,
        getter: () -> GoalConfig,
        setter: (GoalConfig) -> Unit,
        kickGetter: (GoalConfig) -> KickPosition,
        kickSetter: (GoalConfig, KickPosition) -> GoalConfig,
    ): OptionGroup = OptionGroup.createBuilder()
        .name(Component.translatable(headerKey))
        .description(OptionDescription.of(Component.translatable(MatchYaclDesc.desc(headerKey))))
        .apply {
            fun g() = getter()
            fun k() = kickGetter(g())
            addPosition(
                nameKey = "screen.nmbct-football.field.$side.${headerSuffix(headerKey)}_pos",
                descKey = MatchYaclDesc.desc(headerKey),
                getter = { Vec3(k().x, k().y, k().z) },
                setter = { v -> setter(kickSetter(g(), KickPosition(v.x, v.y, v.z))) },
                ctx = ctx,
            )
        }
        .build()

    private fun headerSuffix(headerKey: String): String = when {
        headerKey.contains("gk_kick") -> "gk"
        headerKey.contains("penalty_spot") -> "penalty"
        headerKey.contains("cl_kick") -> "cl"
        headerKey.contains("cr_kick") -> "cr"
        else -> "kick"
    }

    private fun spawnCategory(
        ctx: MatchFieldDraftContext,
        tabKey: String,
        initialPlayers: List<SpawnPosition>,
        getter: () -> TeamSpawnConfig,
        setter: (TeamSpawnConfig) -> Unit,
    ): ConfigCategory {
        val side = if (tabKey.contains("spawn_a")) "spawn_a" else "spawn_b"
        val playerList = ListOption.createBuilder<PositionAndFacing>()
            .name(Component.translatable("screen.nmbct-football.field.plr_header"))
            .description(
                OptionDescription.of(
                    Component.translatable(MatchYaclDesc.desc("screen.nmbct-football.field.plr_header")),
                ),
            )
            .binding(
                initialPlayers.map { it.toPositionAndFacing() },
                { getter().players.map { it.toPositionAndFacing() } },
                { list -> setter(getter().copy(players = list.map { it.toSpawnPosition() })) },
            )
            .customController { opt ->
                PositionAndFacingController.create(opt, showSampleButton = true, compact = true)
            }
            .initial(SpawnPosition.DEFAULT.toPositionAndFacing())
            .build()
        ctx.track(playerList)

        return ConfigCategory.createBuilder()
            .name(Component.translatable(tabKey))
            .group(
                OptionGroup.createBuilder()
                    .name(Component.translatable("screen.nmbct-football.field.gk_header"))
                    .description(OptionDescription.of(Component.translatable(MatchYaclDesc.desc("screen.nmbct-football.field.gk_header"))))
                    .apply {
                        fun g() = getter().gk.toPositionAndFacing()
                        addPositionAndFacing(
                            nameKey = "screen.nmbct-football.field.$side.gk_spawn",
                            descKey = MatchYaclDesc.desc("screen.nmbct-football.field.gk_header"),
                            getter = { g() },
                            setter = { v -> setter(getter().copy(gk = v.toSpawnPosition())) },
                            ctx = ctx,
                        )
                    }
                    .build(),
            )
            .group(playerList)
            .build()
    }

    private fun kickOffCategory(ctx: MatchFieldDraftContext): ConfigCategory = ConfigCategory.createBuilder()
        .name(Component.translatable("screen.nmbct-football.field.tab.kick_off"))
        .group(
            OptionGroup.createBuilder()
                .name(Component.translatable("screen.nmbct-football.field.tab.kick_off"))
                .description(OptionDescription.of(Component.translatable(MatchYaclDesc.desc("screen.nmbct-football.field.tab.kick_off"))))
                .apply {
                    fun ko() = ctx.draft.kickOff
                    addPosition(
                        nameKey = "screen.nmbct-football.field.kick_off.pos",
                        descKey = MatchYaclDesc.desc("screen.nmbct-football.field.tab.kick_off"),
                        getter = { Vec3(ko().x, ko().y, ko().z) },
                        setter = { v -> ctx.draft = ctx.draft.copy(kickOff = KickPosition(v.x, v.y, v.z)) },
                        ctx = ctx,
                    )
                }
                .build(),
        )
        .build()

    private fun sidelineCategory(
        ctx: MatchFieldDraftContext,
        tabKey: String,
        getter: () -> SidelineConfig,
        setter: (SidelineConfig) -> Unit,
    ): ConfigCategory = ConfigCategory.createBuilder()
        .name(Component.translatable(tabKey))
        .group(
            OptionGroup.createBuilder()
                .name(Component.translatable("screen.nmbct-football.field.sideline.coord"))
                .description(OptionDescription.of(Component.translatable(MatchYaclDesc.desc("screen.nmbct-football.field.sideline.coord"))))
                .apply {
                    fun s() = getter()
                    addDoubleField(
                        nameKey = "screen.nmbct-football.field.sideline.coord",
                        descKey = MatchYaclDesc.desc("screen.nmbct-football.field.sideline.coord"),
                        getter = { s().coord },
                        setter = { v -> setter(s().copy(coord = v)) },
                        ctx = ctx,
                    )
                    addFieldButton(ctx, "screen.nmbct-football.field.use_current_pos") {
                        val coord = MatchFieldPlayerSamples.sidelineCoord(s().axis) ?: return@addFieldButton
                        setter(getter().copy(coord = coord))
                    }
                    addEnum(
                        nameKey = "screen.nmbct-football.field.sideline.axis",
                        descKey = MatchYaclDesc.desc("screen.nmbct-football.field.sideline.axis"),
                        enumClass = SidelineAxis::class.java,
                        defaultValue = SidelineAxis.Z,
                        getter = { SidelineAxis.fromString(s().axis) },
                        setter = { axis -> setter(s().copy(axis = axis.id)) },
                        valueName = { axis -> Component.translatable(axis.translationKey) },
                        ctx = ctx,
                    )
                    addBoolean(
                        nameKey = "screen.nmbct-football.field.sideline.inside",
                        descKey = MatchYaclDesc.desc("screen.nmbct-football.field.sideline.inside"),
                        defaultValue = SidelineConfig.DEFAULT.positiveInside,
                        getter = { s().positiveInside },
                        setter = { v -> setter(s().copy(positiveInside = v)) },
                        ctx = ctx,
                    )
                }
                .build(),
        )
        .build()
}
