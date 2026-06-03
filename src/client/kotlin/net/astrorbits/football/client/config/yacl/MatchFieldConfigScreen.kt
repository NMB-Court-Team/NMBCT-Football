package net.astrorbits.football.client.config.yacl

import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.ListOption
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.OptionGroup
import dev.isxander.yacl3.api.YetAnotherConfigLib
import net.astrorbits.football.match.GoalConfig
import net.astrorbits.football.match.KickPosition
import net.astrorbits.football.match.MatchConfig
import net.astrorbits.football.match.SidelineConfig
import net.astrorbits.football.match.SpawnPosition
import net.astrorbits.football.match.TeamSpawnConfig
import net.astrorbits.football.client.util.YaclOptionUtil.addBoolean
import net.astrorbits.football.client.util.YaclOptionUtil.addDouble
import net.astrorbits.football.client.util.YaclOptionUtil.addEnum
import net.astrorbits.football.client.util.YaclOptionUtil.addFloat
import net.astrorbits.football.network.MatchConfigApplyC2SPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

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
                        addFieldButton(ctx, "screen.nmbct-football.field.set_corner1") {
                            val pos = MatchFieldPlayerSamples.position() ?: return@addFieldButton
                            setter(getter().copy(x1 = pos.x, y1 = pos.y, z1 = pos.z))
                        }
                        addFieldButton(ctx, "screen.nmbct-football.field.set_corner2") {
                            val pos = MatchFieldPlayerSamples.position() ?: return@addFieldButton
                            setter(getter().copy(x2 = pos.x, y2 = pos.y, z2 = pos.z))
                        }
                        addGoalCorners(ctx, side, getter, setter)
                    }
                    .build(),
            )
            .group(kickPointGroup(ctx, side, "screen.nmbct-football.field.gk_kick_header", getter, setter, { it.goalKick }, { g, k -> g.copy(goalKick = k) }, "gk"))
            .group(kickPointGroup(ctx, side, "screen.nmbct-football.field.cl_kick_header", getter, setter, { it.cornerKickLeft }, { g, k -> g.copy(cornerKickLeft = k) }, "cl"))
            .group(kickPointGroup(ctx, side, "screen.nmbct-football.field.cr_kick_header", getter, setter, { it.cornerKickRight }, { g, k -> g.copy(cornerKickRight = k) }, "cr"))
            .build()
    }

    private fun OptionGroup.Builder.addGoalCorners(
        ctx: MatchFieldDraftContext,
        side: String,
        getter: () -> GoalConfig,
        setter: (GoalConfig) -> Unit,
    ) {
        fun g() = getter()
        fieldDouble(ctx, "screen.nmbct-football.field.$side.x1", { g().x1 }, { v -> setter(g().copy(x1 = v)) }, COORD_RANGE)
        fieldDouble(ctx, "screen.nmbct-football.field.$side.y1", { g().y1 }, { v -> setter(g().copy(y1 = v)) }, Y_RANGE)
        fieldDouble(ctx, "screen.nmbct-football.field.$side.z1", { g().z1 }, { v -> setter(g().copy(z1 = v)) }, COORD_RANGE)
        fieldDouble(ctx, "screen.nmbct-football.field.$side.x2", { g().x2 }, { v -> setter(g().copy(x2 = v)) }, COORD_RANGE)
        fieldDouble(ctx, "screen.nmbct-football.field.$side.y2", { g().y2 }, { v -> setter(g().copy(y2 = v)) }, Y_RANGE)
        fieldDouble(ctx, "screen.nmbct-football.field.$side.z2", { g().z2 }, { v -> setter(g().copy(z2 = v)) }, COORD_RANGE)
        fieldDouble(ctx, "screen.nmbct-football.field.$side.fx", { g().facingX }, { v -> setter(g().copy(facingX = v)) }, FACING_RANGE, step = 0.1)
        fieldDouble(ctx, "screen.nmbct-football.field.$side.fy", { g().facingY }, { v -> setter(g().copy(facingY = v)) }, FACING_RANGE, step = 0.1)
        fieldDouble(ctx, "screen.nmbct-football.field.$side.fz", { g().facingZ }, { v -> setter(g().copy(facingZ = v)) }, FACING_RANGE, step = 0.1)
    }

    private fun kickPointGroup(
        ctx: MatchFieldDraftContext,
        side: String,
        headerKey: String,
        getter: () -> GoalConfig,
        setter: (GoalConfig) -> Unit,
        kickGetter: (GoalConfig) -> KickPosition,
        kickSetter: (GoalConfig, KickPosition) -> GoalConfig,
        suffix: String,
    ) = OptionGroup.createBuilder()
        .name(Component.translatable(headerKey))
        .description(OptionDescription.of(Component.translatable(MatchYaclDesc.desc(headerKey))))
        .apply { addKickPoint(ctx, side, suffix, getter, setter, kickGetter, kickSetter) }
        .build()

    private fun OptionGroup.Builder.addKickPoint(
        ctx: MatchFieldDraftContext,
        side: String,
        suffix: String,
        getter: () -> GoalConfig,
        setter: (GoalConfig) -> Unit,
        kickGetter: (GoalConfig) -> KickPosition,
        kickSetter: (GoalConfig, KickPosition) -> GoalConfig,
    ) {
        fun g() = getter()
        fun k() = kickGetter(g())
        fieldDouble(ctx, "screen.nmbct-football.field.$side.${suffix}_x", { k().x }, { v -> setter(kickSetter(g(), k().copy(x = v))) }, COORD_RANGE)
        fieldDouble(ctx, "screen.nmbct-football.field.$side.${suffix}_y", { k().y }, { v -> setter(kickSetter(g(), k().copy(y = v))) }, Y_RANGE)
        fieldDouble(ctx, "screen.nmbct-football.field.$side.${suffix}_z", { k().z }, { v -> setter(kickSetter(g(), k().copy(z = v))) }, COORD_RANGE)
        addFieldButton(ctx, "screen.nmbct-football.field.use_current_pos") {
            val pos = MatchFieldPlayerSamples.position() ?: return@addFieldButton
            setter(kickSetter(getter(), KickPosition(pos.x, pos.y, pos.z)))
        }
    }

    private fun spawnCategory(
        ctx: MatchFieldDraftContext,
        tabKey: String,
        initialPlayers: List<SpawnPosition>,
        getter: () -> TeamSpawnConfig,
        setter: (TeamSpawnConfig) -> Unit,
    ): ConfigCategory {
        val side = if (tabKey.contains("spawn_a")) "spawn_a" else "spawn_b"
        val playerList = ListOption.createBuilder<SpawnPosition>()
            .name(Component.translatable("screen.nmbct-football.field.plr_header"))
            .description(
                OptionDescription.of(
                    Component.translatable(MatchYaclDesc.desc("screen.nmbct-football.field.plr_header")),
                ),
            )
            .binding(
                initialPlayers,
                { getter().players },
                { players -> setter(getter().copy(players = players)) },
            )
            .customController { opt -> SpawnPositionListController.create(opt) }
            .initial(SpawnPosition.DEFAULT)
            .build()
        ctx.track(playerList)

        return ConfigCategory.createBuilder()
            .name(Component.translatable(tabKey))
            .group(
                OptionGroup.createBuilder()
                    .name(Component.translatable("screen.nmbct-football.field.gk_header"))
                    .description(OptionDescription.of(Component.translatable(MatchYaclDesc.desc("screen.nmbct-football.field.gk_header"))))
                    .apply { addGkSpawn(ctx, side, getter, setter) }
                    .build(),
            )
            .group(playerList)
            .build()
    }

    private fun OptionGroup.Builder.addGkSpawn(
        ctx: MatchFieldDraftContext,
        side: String,
        getter: () -> TeamSpawnConfig,
        setter: (TeamSpawnConfig) -> Unit,
    ) {
        fun g() = getter().gk
        fun setGk(gk: SpawnPosition) = setter(getter().copy(gk = gk))
        fieldDouble(ctx, "screen.nmbct-football.field.$side.gk_x", { g().x }, { v -> setGk(g().copy(x = v)) }, COORD_RANGE)
        fieldDouble(ctx, "screen.nmbct-football.field.$side.gk_y", { g().y }, { v -> setGk(g().copy(y = v)) }, Y_RANGE)
        fieldDouble(ctx, "screen.nmbct-football.field.$side.gk_z", { g().z }, { v -> setGk(g().copy(z = v)) }, COORD_RANGE)
        fieldFloat(ctx, "screen.nmbct-football.field.$side.gk_yaw", { g().yaw }, { v -> setGk(g().copy(yaw = v)) }, -180f..180f)
        fieldFloat(ctx, "screen.nmbct-football.field.$side.gk_pitch", { g().pitch }, { v -> setGk(g().copy(pitch = v)) }, -90f..90f)
        addFieldButton(ctx, "screen.nmbct-football.field.use_current_pos") {
            val sample = MatchFieldPlayerSamples.spawnWithFacing() ?: return@addFieldButton
            setGk(sample)
        }
    }

    private fun kickOffCategory(ctx: MatchFieldDraftContext): ConfigCategory = ConfigCategory.createBuilder()
        .name(Component.translatable("screen.nmbct-football.field.tab.kick_off"))
        .group(
            OptionGroup.createBuilder()
                .name(Component.translatable("screen.nmbct-football.field.tab.kick_off"))
                .description(OptionDescription.of(Component.translatable(MatchYaclDesc.desc("screen.nmbct-football.field.tab.kick_off"))))
                .apply {
                    fun ko() = ctx.draft.kickOff
                    fieldDouble(ctx, "screen.nmbct-football.field.kick_off.x", { ko().x }, { v -> ctx.draft = ctx.draft.copy(kickOff = ko().copy(x = v)) }, COORD_RANGE)
                    fieldDouble(ctx, "screen.nmbct-football.field.kick_off.y", { ko().y }, { v -> ctx.draft = ctx.draft.copy(kickOff = ko().copy(y = v)) }, Y_RANGE)
                    fieldDouble(ctx, "screen.nmbct-football.field.kick_off.z", { ko().z }, { v -> ctx.draft = ctx.draft.copy(kickOff = ko().copy(z = v)) }, COORD_RANGE)
                    addFieldButton(ctx, "screen.nmbct-football.field.use_current_pos") {
                        val pos = MatchFieldPlayerSamples.position() ?: return@addFieldButton
                        ctx.draft = ctx.draft.copy(kickOff = KickPosition(pos.x, pos.y, pos.z))
                    }
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
                    fieldDouble(ctx, "screen.nmbct-football.field.sideline.coord", { s().coord }, { v -> setter(s().copy(coord = v)) }, COORD_RANGE)
                    addFieldButton(ctx, "screen.nmbct-football.field.use_current_pos") {
                        val coord = MatchFieldPlayerSamples.sidelineCoord(s().axis) ?: return@addFieldButton
                        setter(getter().copy(coord = coord))
                    }
                    addEnum(
                        nameKey = "screen.nmbct-football.field.sideline.axis",
                        descKey = MatchYaclDesc.desc("screen.nmbct-football.field.sideline.axis"),
                        enumClass = SidelineAxis::class.java,
                        defaultValue = SidelineAxis.X,
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

    private fun OptionGroup.Builder.fieldDouble(
        ctx: MatchFieldDraftContext,
        key: String,
        getter: () -> Double,
        setter: (Double) -> Unit,
        range: ClosedFloatingPointRange<Double>,
        step: Double = COORD_STEP,
    ) {
        addDouble(
            nameKey = key,
            descKey = MatchYaclDesc.desc(key),
            defaultValue = getter(),
            getter = getter,
            setter = setter,
            range = range,
            step = step,
            ctx = ctx,
        )
    }

    private fun OptionGroup.Builder.fieldFloat(
        ctx: MatchFieldDraftContext,
        key: String,
        getter: () -> Float,
        setter: (Float) -> Unit,
        range: ClosedFloatingPointRange<Float>,
        step: Float = 1f,
    ) {
        addFloat(
            nameKey = key,
            descKey = MatchYaclDesc.desc(key),
            defaultValue = getter(),
            getter = getter,
            setter = setter,
            range = range,
            step = step,
            ctx = ctx,
        )
    }

    private val COORD_RANGE = -3000.0..3000.0
    private val Y_RANGE = -64.0..320.0
    private val FACING_RANGE = -1.0..1.0
    private const val COORD_STEP = 0.1
}
