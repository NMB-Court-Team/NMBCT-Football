package net.astrorbits.football.client.util

import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.OptionGroup
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder
import dev.isxander.yacl3.api.controller.EnumControllerBuilder
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder
import dev.isxander.yacl3.api.controller.LongSliderControllerBuilder
import net.minecraft.network.chat.Component

object YaclOptionUtil {
    fun OptionGroup.Builder.addDouble(
        nameKey: String,
        descKey: String,
        defaultValue: Double,
        getter: () -> Double,
        setter: (Double) -> Unit,
        range: ClosedFloatingPointRange<Double>,
        step: Double = (range.endInclusive - range.start) / 100.0,
    ) {
        option(
            Option.createBuilder<Double>()
                .name(Component.translatable(nameKey))
                .description(OptionDescription.of(Component.translatable(descKey)))
                .binding(defaultValue, getter, setter)
                .controller { opt ->
                    DoubleSliderControllerBuilder.create(opt)
                        .range(range.start, range.endInclusive)
                        .step(step)
                }
                .build(),
        )
    }

    fun OptionGroup.Builder.addInt(
        nameKey: String,
        descKey: String,
        defaultValue: Int,
        getter: () -> Int,
        setter: (Int) -> Unit,
        range: IntRange,
    ) {
        option(
            Option.createBuilder<Int>()
                .name(Component.translatable(nameKey))
                .description(OptionDescription.of(Component.translatable(descKey)))
                .binding(defaultValue, getter, setter)
                .controller { opt ->
                    IntegerSliderControllerBuilder.create(opt)
                        .range(range.first, range.last)
                        .step(1)
                }
                .build(),
        )
    }

    fun OptionGroup.Builder.addLong(
        nameKey: String,
        descKey: String,
        defaultValue: Long,
        getter: () -> Long,
        setter: (Long) -> Unit,
        range: LongRange,
        step: Long = 10L,
    ) {
        option(
            Option.createBuilder<Long>()
                .name(Component.translatable(nameKey))
                .description(OptionDescription.of(Component.translatable(descKey)))
                .binding(defaultValue, getter, setter)
                .controller { opt ->
                    LongSliderControllerBuilder.create(opt)
                        .range(range.first, range.last)
                        .step(step)
                }
                .build(),
        )
    }

    fun OptionGroup.Builder.addFloat(
        nameKey: String,
        descKey: String,
        defaultValue: Float,
        getter: () -> Float,
        setter: (Float) -> Unit,
        range: ClosedFloatingPointRange<Float>,
        step: Float = (range.endInclusive - range.start) / 100f,
    ) {
        option(
            Option.createBuilder<Float>()
                .name(Component.translatable(nameKey))
                .description(OptionDescription.of(Component.translatable(descKey)))
                .binding(defaultValue, getter, setter)
                .controller { opt ->
                    FloatSliderControllerBuilder.create(opt)
                        .range(range.start, range.endInclusive)
                        .step(step)
                }
                .build(),
        )
    }

    fun <T : Enum<T>> OptionGroup.Builder.addEnum(
        nameKey: String,
        descKey: String,
        enumClass: Class<T>,
        defaultValue: T,
        getter: () -> T,
        setter: (T) -> Unit,
        valueName: (T) -> Component,
    ) {
        option(
            Option.createBuilder<T>()
                .name(Component.translatable(nameKey))
                .description(OptionDescription.of(Component.translatable(descKey)))
                .binding(defaultValue, getter, setter)
                .controller { opt ->
                    EnumControllerBuilder.create(opt)
                        .enumClass(enumClass)
                        .formatValue(valueName)
                }
                .build(),
        )
    }
}