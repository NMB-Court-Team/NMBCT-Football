package net.astrorbits.football.client.util

import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.OptionGroup
import dev.isxander.yacl3.api.controller.*
import net.astrorbits.football.client.config.yacl.MatchFieldDraftContext
import net.minecraft.network.chat.Component

object YaclOptionUtil {
    fun OptionGroup.Builder.addDouble(
        nameKey: String,
        descKey: String? = null,
        defaultValue: Double,
        getter: () -> Double,
        setter: (Double) -> Unit,
        range: ClosedFloatingPointRange<Double>,
        step: Double = (range.endInclusive - range.start) / 100.0,
        ctx: MatchFieldDraftContext? = null,
    ) {
        val builder = Option.createBuilder<Double>()
            .name(Component.translatable(nameKey))
            .binding(defaultValue, getter, setter)
        if (descKey != null) {
            builder.description(OptionDescription.of(Component.translatable(descKey)))
        }
        val built = builder
            .controller { opt ->
                DoubleSliderControllerBuilder.create(opt)
                    .range(range.start, range.endInclusive)
                    .step(step)
            }
            .build()
        option(built)
        ctx?.track(built)
    }

    fun OptionGroup.Builder.addInt(
        nameKey: String,
        descKey: String? = null,
        defaultValue: Int,
        getter: () -> Int,
        setter: (Int) -> Unit,
        range: IntRange,
        ctx: MatchFieldDraftContext? = null,
    ) {
        val builder = Option.createBuilder<Int>()
            .name(Component.translatable(nameKey))
            .binding(defaultValue, getter, setter)
        if (descKey != null) {
            builder.description(OptionDescription.of(Component.translatable(descKey)))
        }
        val built = builder
            .controller { opt ->
                IntegerSliderControllerBuilder.create(opt)
                    .range(range.first, range.last)
                    .step(1)
            }
            .build()
        option(built)
        ctx?.track(built)
    }

    fun OptionGroup.Builder.addLong(
        nameKey: String,
        descKey: String? = null,
        defaultValue: Long,
        getter: () -> Long,
        setter: (Long) -> Unit,
        range: LongRange,
        step: Long = 10L,
        ctx: MatchFieldDraftContext? = null,
    ) {
        val builder = Option.createBuilder<Long>()
            .name(Component.translatable(nameKey))
            .binding(defaultValue, getter, setter)
        if (descKey != null) {
            builder.description(OptionDescription.of(Component.translatable(descKey)))
        }
        val built = builder
            .controller { opt ->
                LongSliderControllerBuilder.create(opt)
                    .range(range.first, range.last)
                    .step(step)
            }
            .build()
        option(built)
        ctx?.track(built)
    }

    fun OptionGroup.Builder.addFloat(
        nameKey: String,
        descKey: String? = null,
        defaultValue: Float,
        getter: () -> Float,
        setter: (Float) -> Unit,
        range: ClosedFloatingPointRange<Float>,
        step: Float = (range.endInclusive - range.start) / 100f,
        ctx: MatchFieldDraftContext? = null,
    ) {
        val builder = Option.createBuilder<Float>()
            .name(Component.translatable(nameKey))
            .binding(defaultValue, getter, setter)
        if (descKey != null) {
            builder.description(OptionDescription.of(Component.translatable(descKey)))
        }
        val built = builder
            .controller { opt ->
                FloatSliderControllerBuilder.create(opt)
                    .range(range.start, range.endInclusive)
                    .step(step)
            }
            .build()
        option(built)
        ctx?.track(built)
    }

    fun <T : Enum<T>> OptionGroup.Builder.addEnum(
        nameKey: String,
        descKey: String? = null,
        enumClass: Class<T>,
        defaultValue: T,
        getter: () -> T,
        setter: (T) -> Unit,
        valueName: (T) -> Component,
        ctx: MatchFieldDraftContext? = null,
    ) {
        val builder = Option.createBuilder<T>()
            .name(Component.translatable(nameKey))
            .binding(defaultValue, getter, setter)
        if (descKey != null) {
            builder.description(OptionDescription.of(Component.translatable(descKey)))
        }
        val built = builder
            .controller { opt ->
                EnumControllerBuilder.create(opt)
                    .enumClass(enumClass)
                    .formatValue(valueName)
            }
            .build()
        option(built)
        ctx?.track(built)
    }

    fun OptionGroup.Builder.addBoolean(
        nameKey: String,
        descKey: String? = null,
        defaultValue: Boolean,
        getter: () -> Boolean,
        setter: (Boolean) -> Unit,
        ctx: MatchFieldDraftContext? = null,
    ) {
        val builder = Option.createBuilder<Boolean>()
            .name(Component.translatable(nameKey))
            .binding(defaultValue, getter, setter)
            .controller { opt -> TickBoxControllerBuilder.create(opt) }
        if (descKey != null) {
            builder.description(OptionDescription.of(Component.translatable(descKey)))
        }
        val built = builder.build()
        option(built)
        ctx?.track(built)
    }

    fun OptionGroup.Builder.addString(
        nameKey: String,
        descKey: String? = null,
        defaultValue: String,
        getter: () -> String,
        setter: (String) -> Unit,
        ctx: MatchFieldDraftContext? = null,
    ) {
        val builder = Option.createBuilder<String>()
            .name(Component.translatable(nameKey))
            .binding(defaultValue, getter, setter)
        if (descKey != null) {
            builder.description(OptionDescription.of(Component.translatable(descKey)))
        }
        val built = builder
            .controller { opt -> StringControllerBuilder.create(opt) }
            .build()
        option(built)
        ctx?.track(built)
    }
}