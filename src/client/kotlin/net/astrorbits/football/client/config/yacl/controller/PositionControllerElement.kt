package net.astrorbits.football.client.config.yacl.controller

import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.utils.Dimension
import dev.isxander.yacl3.gui.YACLScreen
import dev.isxander.yacl3.gui.controllers.ControllerWidget
import net.astrorbits.football.client.config.yacl.MatchFieldPlayerSamples
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3

/**
 * 布局：[ 选项名 | X ___ Y ___ Z ___ | 取当前位置 ]
 * - 常规选项：未悬停时右侧显示摘要；悬停或正在编辑时显示内联字段。
 * - [compact]：列表项等固定行高场景，始终显示内联字段。
 */
class PositionControllerElement(
    control: PositionController,
    screen: YACLScreen,
    dim: Dimension<Int>,
) : ControllerWidget<PositionController>(control, screen, dim), InlineFieldKeyboardHost {
    private val posOption: Option<Vec3> = control.option()
    private val compact: Boolean = control.compact
    private val showSampleButton: Boolean = control.showSampleButton

    private lateinit var xField: InlineNumberField
    private lateinit var yField: InlineNumberField
    private lateinit var zField: InlineNumberField
    private var sampleButton: Button? = null

    init {
        initFields()
        posOption.addEventListener { _, _ -> syncFields() }
    }

    /** 仅构造一次；布局在 [layoutAndRenderFields] 里用 [InlineNumberField.setDimension] 更新。 */
    private fun initFields() {
        val placeholder = Dimension.ofInt(0, 0, FIELD_WIDTH, MIN_ROW_HEIGHT)
        xField = InlineNumberField.doubleField(placeholder, "X", { posOption.pendingValue().x }) { v ->
            val cur = posOption.pendingValue()
            posOption.requestSet(Vec3(v, cur.y, cur.z))
        }
        yField = InlineNumberField.doubleField(placeholder, "Y", { posOption.pendingValue().y }) { v ->
            val cur = posOption.pendingValue()
            posOption.requestSet(Vec3(cur.x, v, cur.z))
        }
        zField = InlineNumberField.doubleField(placeholder, "Z", { posOption.pendingValue().z }) { v ->
            val cur = posOption.pendingValue()
            posOption.requestSet(Vec3(cur.x, cur.y, v))
        }
        syncFields()
        sampleButton = if (showSampleButton) {
            Button.builder(Component.translatable(USE_POS_KEY)) {
                MatchFieldPlayerSamples.position()?.let { kick ->
                    posOption.requestSet(Vec3(kick.x, kick.y, kick.z))
                    syncFields()
                }
            }.bounds(0, 0, SAMPLE_BUTTON_WIDTH, MIN_ROW_HEIGHT).build()
        } else {
            null
        }
    }

    private fun activeField(): InlineNumberField? =
        listOf(xField, yField, zField).firstOrNull { it.isFieldFocused() }

    private fun syncFields() {
        xField.syncFromValue()
        yField.syncFromValue()
        zField.syncFromValue()
    }

    private fun isEditing(): Boolean =
        compact || isHovered || xField.isFieldFocused() || yField.isFieldFocused() || zField.isFieldFocused()

    override fun extractValueText(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        if (!isEditing()) {
            super.extractValueText(graphics, mouseX, mouseY, delta)
            return
        }
        layoutAndRenderFields(graphics, mouseX, mouseY, delta)
    }

    override fun extractHoveredControl(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        if (isEditing()) {
            layoutAndRenderFields(graphics, mouseX, mouseY, delta)
        }
    }

    private fun layoutAndRenderFields(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        @Suppress("UNCHECKED_CAST")
        val dim = dimension as Dimension<Int>
        val slices = layoutSlices(dim, editing = true)
        val rowHighlight = isHovered || isFocused
        xField.dimension = slices[0]
        yField.dimension = slices[1]
        zField.dimension = slices[2]
        xField.rowHighlight = rowHighlight
        yField.rowHighlight = rowHighlight
        zField.rowHighlight = rowHighlight
        xField.extractRenderState(graphics, mouseX, mouseY, delta)
        yField.extractRenderState(graphics, mouseX, mouseY, delta)
        zField.extractRenderState(graphics, mouseX, mouseY, delta)
        sampleButton?.let { btn ->
            val btnDim = slices[3]
            btn.setPosition(btnDim.x(), btnDim.y())
            btn.setWidth(btnDim.width())
            btn.setHeight(btnDim.height())
            btn.extractRenderState(graphics, mouseX, mouseY, delta)
        }
    }

    private fun layoutSlices(dim: Dimension<Int>, editing: Boolean): List<Dimension<Int>> {
        val y = dim.y()
        val h = dim.height().coerceAtLeast(MIN_ROW_HEIGHT)
        var right = dim.xLimit() - xPadding
        val slices = mutableListOf<Dimension<Int>>()

        if (showSampleButton && editing) {
            right -= SAMPLE_BUTTON_WIDTH
            slices.add(Dimension.ofInt(right, y, SAMPLE_BUTTON_WIDTH, h))
            right -= FIELD_GAP
        }

        repeat(3) {
            right -= FIELD_WIDTH
            slices.add(0, Dimension.ofInt(right, y, FIELD_WIDTH, h))
            right -= FIELD_GAP
        }
        return slices
    }

    override fun getHoveredControlWidth(): Int {
        if (!isEditing()) {
            return textRenderer.width(control.formatValue().string).coerceAtMost(controlAreaWidth())
        }
        var w = FIELD_WIDTH * 3 + FIELD_GAP * 2
        if (showSampleButton) w += SAMPLE_BUTTON_WIDTH + FIELD_GAP
        return w.coerceAtMost(controlAreaWidth())
    }

    override fun getUnhoveredControlWidth(): Int =
        if (compact) getHoveredControlWidth() else textRenderer.width(control.formatValue().string).coerceAtMost(controlAreaWidth())

    private fun controlAreaWidth(): Int {
        @Suppress("UNCHECKED_CAST")
        val dim = dimension as Dimension<Int>
        return if (optionNameString.isEmpty()) dim.width() - xPadding * 2 else dim.width() * 5 / 8
    }

    override fun unfocus() {
        super.unfocus()
        xField.unfocus()
        yField.unfocus()
        zField.unfocus()
        sampleButton?.isFocused = false
    }

    override fun isFocused(): Boolean =
        focused || xField.isFocused || yField.isFocused || zField.isFocused || sampleButton?.isFocused == true

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        if (!isAvailable || !dimension.isPointInside(event.x().toInt(), event.y().toInt())) {
            unfocus()
            return false
        }
        if (sampleButton?.mouseClicked(event, doubled) == true) return true
        if (delegateFieldClick(zField, event, doubled)) return true
        if (delegateFieldClick(yField, event, doubled)) return true
        if (delegateFieldClick(xField, event, doubled)) return true
        if (!isEditing()) {
            focused = true
            return true
        }
        return false
    }

    private fun delegateFieldClick(field: InlineNumberField, event: MouseButtonEvent, doubled: Boolean): Boolean {
        if (!field.mouseClicked(event, doubled)) return false
        if (field !== xField) xField.unfocus()
        if (field !== yField) yField.unfocus()
        if (field !== zField) zField.unfocus()
        focused = true
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean =
        sampleButton?.mouseReleased(event) == true ||
            zField.mouseReleased(event) ||
            yField.mouseReleased(event) ||
            xField.mouseReleased(event)

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean =
        sampleButton?.mouseDragged(event, deltaX, deltaY) == true ||
            zField.mouseDragged(event, deltaX, deltaY) ||
            yField.mouseDragged(event, deltaX, deltaY) ||
            xField.mouseDragged(event, deltaX, deltaY)

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean =
        sampleButton?.mouseScrolled(mouseX, mouseY, horizontal, vertical) == true ||
            zField.mouseScrolled(mouseX, mouseY, horizontal, vertical) ||
            yField.mouseScrolled(mouseX, mouseY, horizontal, vertical) ||
            xField.mouseScrolled(mouseX, mouseY, horizontal, vertical)

    override fun hasActiveInlineField(): Boolean = activeField() != null

    override fun handleKeyPressed(event: KeyEvent): Boolean = keyPressed(event)

    override fun handleCharTyped(event: CharacterEvent): Boolean = charTyped(event)

    override fun keyPressed(event: KeyEvent): Boolean {
        activeField()?.let { if (it.keyPressed(event)) return true }
        return sampleButton?.keyPressed(event) == true ||
            zField.keyPressed(event) ||
            yField.keyPressed(event) ||
            xField.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        activeField()?.let { if (it.charTyped(event)) return true }
        return zField.charTyped(event) || yField.charTyped(event) || xField.charTyped(event)
    }

    companion object {
        private const val USE_POS_KEY = "screen.nmbct-football.field.use_current_pos"
        private const val FIELD_WIDTH = 76
        private const val FIELD_GAP = 4
        private const val SAMPLE_BUTTON_WIDTH = 88
        private const val MIN_ROW_HEIGHT = 18
    }
}
