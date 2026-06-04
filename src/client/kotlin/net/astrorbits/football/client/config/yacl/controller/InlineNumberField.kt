package net.astrorbits.football.client.config.yacl.controller

import com.mojang.blaze3d.platform.InputConstants
import dev.isxander.yacl3.api.utils.Dimension
import dev.isxander.yacl3.gui.AbstractWidget
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.ParsePosition

/**
 * 单行内联数字输入：`标签` + 可编辑文本（无 YACL 选项行背景）。
 */
class InlineNumberField(
    dim: Dimension<Int>,
    private val label: Component,
    private val readValue: () -> String,
    private val writeValue: (String) -> Unit,
    private val isInputValid: (String) -> Boolean,
) : AbstractWidget(dim) {
    var inputField: String = readValue()
        private set
    private var inputFieldFocused = false
    private var caretPos = 0
    private var selectionLength = 0
    private var renderOffset = 0
    private var caretTicks = 0f
    private var inputBounds: Dimension<Int> = dim

    /** 父行悬停时显示下划线（与 YACL 文本框行为一致）。 */
    var rowHighlight: Boolean = false

    fun syncFromValue() {
        if (!inputFieldFocused) {
            inputField = readValue()
            caretPos = inputField.length
            selectionLength = 0
        }
    }

    fun isFieldFocused(): Boolean = inputFieldFocused

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        updateLayout()
        val dim = dimension
        val textY = getTextY(dim)

        graphics.text(textRenderer, label, dim.xi(), textY, -1, true)

        val display = if (inputFieldFocused) inputField else readValue()
        val textX = inputBounds.xLi() - textRenderer.width(display) + renderOffset
        graphics.enableScissor(
            inputBounds.xi(),
            inputBounds.yi() - 2,
            inputBounds.xLi() + 1,
            inputBounds.yLi() + 4,
        )
        graphics.text(textRenderer, Component.literal(display), textX, textY, -1, true)
        graphics.disableScissor()

        val showUnderline = shouldShowUnderline(mouseX, mouseY)
        if (showUnderline) {
            val x0 = inputBounds.xi()
            val x1 = inputBounds.xLi()
            val yBot = inputBounds.yLi()
            graphics.fill(x0, yBot, x1, yBot + 1, -1)
            graphics.fill(x0 + 1, yBot + 1, x1 + 1, yBot + 2, 0xFF404040.toInt())
        }

        if (inputFieldFocused) {
            val caretEnd: Int = caretPos.coerceIn(0, display.length)
            val caretX: Int = textX + textRenderer.width(display.substring(0, caretEnd))
            caretTicks += delta
            if ((caretTicks.toInt() % 20) <= 10) {
                graphics.fill(
                    caretX,
                    inputBounds.yi() - 2,
                    caretX + 1,
                    inputBounds.yLi() - 1,
                    -1,
                )
            }
        }

        if (isPointInsideWidget(mouseX.toDouble(), mouseY.toDouble())) {
            graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.IBEAM)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        if (!isPointInsideWidget(event.x(), event.y())) return false
        if (!inputFieldFocused) {
            inputField = readValue()
        }
        inputFieldFocused = true
        updateLayout()
        caretPos = if (isPointInsideInput(event.x(), event.y())) {
            caretPosFromClick(event.x())
        } else {
            inputField.length
        }
        selectionLength = 0
        caretTicks = 0f
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean = inputFieldFocused

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean = inputFieldFocused

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean = false

    override fun keyPressed(event: KeyEvent): Boolean {
        if (!inputFieldFocused) return false
        when (event.key()) {
            InputConstants.KEY_ESCAPE, InputConstants.KEY_RETURN -> {
                unfocus()
                return true
            }
            InputConstants.KEY_BACKSPACE -> {
                doBackspace()
                return true
            }
            InputConstants.KEY_DELETE -> {
                doDelete()
                return true
            }
            InputConstants.KEY_LEFT -> {
                if (caretPos > 0) caretPos--
                selectionLength = 0
                return true
            }
            InputConstants.KEY_RIGHT -> {
                if (caretPos < inputField.length) caretPos++
                selectionLength = 0
                return true
            }
            InputConstants.KEY_HOME -> {
                caretPos = 0
                selectionLength = 0
                return true
            }
            InputConstants.KEY_END -> {
                caretPos = inputField.length
                selectionLength = 0
                return true
            }
        }
        if (event.isPaste) {
            pasteFromClipboard()
            return true
        }
        return false
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (!inputFieldFocused) return false
        insertText(event.codepointAsString())
        return true
    }

    override fun setFocused(focused: Boolean) {
        if (!focused) unfocus()
    }

    override fun unfocus() {
        if (!inputFieldFocused) return
        inputFieldFocused = false
        renderOffset = 0
        if (isInputValid(inputField)) {
            writeValue(inputField)
        } else {
            inputField = readValue()
        }
        caretPos = inputField.length
        selectionLength = 0
    }

    override fun isFocused(): Boolean = inputFieldFocused

    private fun shouldShowUnderline(mouseX: Int, mouseY: Int): Boolean =
        inputFieldFocused || rowHighlight || isPointInsideWidget(mouseX.toDouble(), mouseY.toDouble())

    private fun updateLayout() {
        val dim = dimension
        val labelWidth = textRenderer.width(label.string) + LABEL_GAP
        val inputWidth = (dim.wi() - labelWidth).coerceAtLeast(MIN_INPUT_WIDTH)
        inputBounds = Dimension.ofInt(
            dim.xi() + labelWidth,
            dim.centerYi() - textRenderer.lineHeight / 2,
            inputWidth,
            textRenderer.lineHeight,
        )
    }

    private fun insertText(text: String) {
        if (!modifyInput { it.insert(caretPos, text) }) return
        caretPos += text.length
    }

    private fun pasteFromClipboard() {
        insertText(client.keyboardHandler.clipboard)
    }

    private fun doBackspace() {
        if (caretPos > 0 && modifyInput { it.deleteCharAt(caretPos - 1) }) {
            caretPos--
        }
    }

    private fun doDelete() {
        if (caretPos < inputField.length) {
            modifyInput { it.deleteCharAt(caretPos) }
        }
    }

    private fun modifyInput(block: (StringBuilder) -> Unit): Boolean {
        val builder = StringBuilder(inputField)
        block(builder)
        if (!isInputValid(builder.toString())) return false
        inputField = builder.toString()
        return true
    }

    private fun caretPosFromClick(mouseX: Double): Int {
        val display = inputField
        val textX = inputBounds.xLi() - textRenderer.width(display)
        var pos = 0
        var width = 0
        for (ch in display.toCharArray()) {
            val charWidth = textRenderer.width(ch.toString())
            if (width + charWidth / 2 > mouseX - textX) break
            pos++
            width += charWidth
        }
        return pos.coerceIn(0, display.length)
    }

    private fun isPointInsideInput(mouseX: Double, mouseY: Double): Boolean =
        inputBounds.isPointInside(mouseX.toInt(), mouseY.toInt())

    private fun isPointInsideWidget(mouseX: Double, mouseY: Double): Boolean {
        val dim = dimension
        return mouseX >= dim.xi() && mouseX < dim.xLi() &&
            mouseY >= dim.yi() && mouseY < dim.yLi()
    }

    private fun getTextY(dim: Dimension<Int>): Int =
        dim.centerYi() - textRenderer.lineHeight / 2

    companion object {
        private const val LABEL_GAP = 2
        private const val MIN_INPUT_WIDTH = 28

        fun doubleField(
            dim: Dimension<Int>,
            label: String,
            read: () -> Double,
            write: (Double) -> Unit,
        ): InlineNumberField = InlineNumberField(
            dim = dim,
            label = Component.literal(label),
            readValue = { NUMBER_FORMAT.format(read()) },
            writeValue = { text ->
                parseDouble(text)?.let(write)
            },
            isInputValid = ::isValidDoubleInput,
        )

        fun floatField(
            dim: Dimension<Int>,
            label: String,
            read: () -> Float,
            write: (Float) -> Unit,
        ): InlineNumberField = InlineNumberField(
            dim = dim,
            label = Component.literal(label),
            readValue = { NUMBER_FORMAT.format(read()) },
            writeValue = { text ->
                parseFloat(text)?.let(write)
            },
            isInputValid = ::isValidDoubleInput,
        )

        private fun transformInput(input: String): String {
            var s = input.trim()
            if (s.isEmpty()) s = "0"
            if (s == "-") return s
            return s.replace(DECIMAL_SYMBOLS.groupingSeparator.toString(), "")
        }

        private fun isValidDoubleInput(input: String): Boolean {
            val transformed = transformInput(input)
            if (transformed == "-" || transformed.isEmpty()) return true
            val pos = ParsePosition(0)
            NUMBER_FORMAT.parse(transformed, pos)
            return pos.index == transformed.length
        }

        private fun parseDouble(input: String): Double? {
            val transformed = transformInput(input)
            if (transformed == "-") return null
            val pos = ParsePosition(0)
            return NUMBER_FORMAT.parse(transformed, pos)?.toDouble()
        }

        private fun parseFloat(input: String): Float? = parseDouble(input)?.toFloat()
    }
}

private val NUMBER_FORMAT: NumberFormat = NumberFormat.getInstance()
private val DECIMAL_SYMBOLS: DecimalFormatSymbols = DecimalFormatSymbols.getInstance()

@Suppress("UNCHECKED_CAST")
private fun Dimension<*>.xi(): Int = (this as Dimension<Int>).x()

@Suppress("UNCHECKED_CAST")
private fun Dimension<*>.yi(): Int = (this as Dimension<Int>).y()

@Suppress("UNCHECKED_CAST")
private fun Dimension<*>.wi(): Int = (this as Dimension<Int>).width()

@Suppress("UNCHECKED_CAST")
private fun Dimension<*>.xLi(): Int = (this as Dimension<Int>).xLimit()

@Suppress("UNCHECKED_CAST")
private fun Dimension<*>.yLi(): Int = (this as Dimension<Int>).yLimit()

@Suppress("UNCHECKED_CAST")
private fun Dimension<*>.centerYi(): Int = (this as Dimension<Int>).centerY()
