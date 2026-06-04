package net.astrorbits.football.client.config.yacl

import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.OptionGroup
import net.astrorbits.football.client.config.yacl.controller.DoubleStringController
import net.astrorbits.football.client.config.yacl.controller.PositionAndFacing
import net.astrorbits.football.client.config.yacl.controller.PositionAndFacingController
import net.astrorbits.football.client.config.yacl.controller.PositionController
import net.astrorbits.football.match.SpawnPosition
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3

fun SpawnPosition.toPositionAndFacing(): PositionAndFacing =
    PositionAndFacing(Vec3(x, y, z), yaw, pitch)

fun PositionAndFacing.toSpawnPosition(): SpawnPosition =
    SpawnPosition(pos.x, pos.y, pos.z, yaw, pitch)

fun OptionGroup.Builder.addPosition(
    nameKey: String,
    descKey: String?,
    getter: () -> Vec3,
    setter: (Vec3) -> Unit,
    showSampleButton: Boolean = true,
    ctx: MatchFieldDraftContext? = null,
) {
    val built = Option.createBuilder<Vec3>()
        .name(Component.translatable(nameKey))
        .binding(getter(), getter, setter)
        .customController { opt -> PositionController.create(opt, showSampleButton = showSampleButton) }
        .apply {
            if (descKey != null) {
                description(OptionDescription.of(Component.translatable(descKey)))
            }
        }
        .build()
    option(built)
    ctx?.track(built)
}

fun OptionGroup.Builder.addPositionAndFacing(
    nameKey: String,
    descKey: String?,
    getter: () -> PositionAndFacing,
    setter: (PositionAndFacing) -> Unit,
    showSampleButton: Boolean = true,
    compact: Boolean = false,
    ctx: MatchFieldDraftContext? = null,
) {
    val built = Option.createBuilder<PositionAndFacing>()
        .name(Component.translatable(nameKey))
        .binding(getter(), getter, setter)
        .customController { opt ->
            PositionAndFacingController.create(opt, showSampleButton = showSampleButton, compact = compact)
        }
        .apply {
            if (descKey != null) {
                description(OptionDescription.of(Component.translatable(descKey)))
            }
        }
        .build()
    option(built)
    ctx?.track(built)
}

fun OptionGroup.Builder.addDoubleField(
    nameKey: String,
    descKey: String?,
    getter: () -> Double,
    setter: (Double) -> Unit,
    ctx: MatchFieldDraftContext? = null,
) {
    val built = Option.createBuilder<Double>()
        .name(Component.translatable(nameKey))
        .binding(getter(), getter, setter)
        .customController { opt -> DoubleStringController.create(opt) }
        .apply {
            if (descKey != null) {
                description(OptionDescription.of(Component.translatable(descKey)))
            }
        }
        .build()
    option(built)
    ctx?.track(built)
}
