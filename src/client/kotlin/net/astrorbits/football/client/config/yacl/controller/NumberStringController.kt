package net.astrorbits.football.client.config.yacl.controller

import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.utils.Dimension
import dev.isxander.yacl3.gui.AbstractWidget
import dev.isxander.yacl3.gui.YACLScreen
import dev.isxander.yacl3.gui.controllers.string.IStringController
import dev.isxander.yacl3.gui.controllers.string.StringControllerElement
import net.minecraft.network.chat.Component
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.ParsePosition

private val NUMBER_FORMAT: NumberFormat = NumberFormat.getInstance()
private val DECIMAL_SYMBOLS: DecimalFormatSymbols = DecimalFormatSymbols.getInstance()

/**
 * 文本框数字控制器：结束编辑时校验；非法输入回退为上一次合法值。
 */
abstract class NumberStringController<N>(
    protected val option: Option<N>,
    private val formatValue: (N) -> String,
    private val parseValue: (String) -> N?,
) : IStringController<N> where N : Number, N : Comparable<N> {

    override fun option(): Option<N> = option

    override fun getString(): String = formatValue(option.pendingValue())

    override fun setFromString(value: String) {
        parseValue(transformInput(value))?.let { option.requestSet(it) }
    }

    override fun isInputValid(input: String): Boolean {
        val transformed = transformInput(input)
        if (transformed == "-" || transformed.isEmpty()) return true
        val pos = ParsePosition(0)
        NUMBER_FORMAT.parse(transformed, pos)
        return pos.index == transformed.length
    }

    override fun formatValue(): Component = Component.literal(getString())

    override fun provideWidget(screen: YACLScreen, widgetDimension: Dimension<Int>): AbstractWidget =
        RevertOnInvalidStringControllerElement(this, screen, widgetDimension)

    protected open fun transformInput(input: String): String {
        var s = input.trim()
        if (s.isEmpty()) s = "0"
        if (s == "-") return s
        return s.replace(DECIMAL_SYMBOLS.groupingSeparator.toString(), "")
    }
}

class IntStringController(option: Option<Int>) : NumberStringController<Int>(
    option,
    formatValue = { it.toString() },
    parseValue = { input ->
        val pos = ParsePosition(0)
        NUMBER_FORMAT.parse(input, pos)?.toInt()
    },
) {
    companion object {
        fun create(option: Option<Int>): IntStringController = IntStringController(option)
    }
}

class FloatStringController(option: Option<Float>) : NumberStringController<Float>(
    option,
    formatValue = { NUMBER_FORMAT.format(it) },
    parseValue = { input ->
        val pos = ParsePosition(0)
        NUMBER_FORMAT.parse(input, pos)?.toFloat()
    },
) {
    companion object {
        fun create(option: Option<Float>): FloatStringController = FloatStringController(option)
    }
}

class DoubleStringController(option: Option<Double>) : NumberStringController<Double>(
    option,
    formatValue = { NUMBER_FORMAT.format(it) },
    parseValue = { input ->
        val pos = ParsePosition(0)
        NUMBER_FORMAT.parse(input, pos)?.toDouble()
    },
) {
    companion object {
        fun create(option: Option<Double>): DoubleStringController = DoubleStringController(option)
    }
}

private class RevertOnInvalidStringControllerElement(
    control: IStringController<*>,
    screen: YACLScreen,
    dim: Dimension<Int>,
) : StringControllerElement(control, screen, dim, false) {

    override fun unfocus() {
        inputFieldFocused = false
        renderOffset = 0
        focused = false
        if (control.isInputValid(inputField)) {
            control.setFromString(inputField)
        } else {
            inputField = control.string
        }
    }
}
