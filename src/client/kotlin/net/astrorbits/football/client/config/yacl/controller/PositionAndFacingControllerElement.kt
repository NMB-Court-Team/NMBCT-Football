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
 * 布局：[ 选项名 | X ___ Y ___ Z ___ 偏航 ___ 俯仰 ___ | 取当前位置与朝向 ]
 */
class PositionAndFacingControllerElement(
    control: PositionAndFacingController,
    screen: YACLScreen,
    dim: Dimension<Int>,
) : ControllerWidget<PositionAndFacingController>(control, screen, dim), InlineFieldKeyboardHost {
    private val valueOption: Option<PositionAndFacing> = control.option()
    private val compact: Boolean = control.compact
    private val showSampleButton: Boolean = control.showSampleButton

    private lateinit var xField: InlineNumberField
    private lateinit var yField: InlineNumberField
    private lateinit var zField: InlineNumberField
    private lateinit var yawField: InlineNumberField
    private lateinit var pitchField: InlineNumberField
    private var sampleButton: Button? = null

    init {
        initFields()
        valueOption.addEventListener { _, _ -> syncFields() }
    }

    private fun initFields() {
        val fieldWidth = if (compact) COMPACT_FIELD_WIDTH else FIELD_WIDTH
        val placeholder = Dimension.ofInt(0, 0, fieldWidth, MIN_ROW_HEIGHT)
        xField = InlineNumberField.doubleField(placeholder, "X", { valueOption.pendingValue().pos.x }) { x ->
            val cur = valueOption.pendingValue()
            valueOption.requestSet(cur.copy(pos = Vec3(x, cur.pos.y, cur.pos.z)))
        }
        yField = InlineNumberField.doubleField(placeholder, "Y", { valueOption.pendingValue().pos.y }) { y ->
            val cur = valueOption.pendingValue()
            valueOption.requestSet(cur.copy(pos = Vec3(cur.pos.x, y, cur.pos.z)))
        }
        zField = InlineNumberField.doubleField(placeholder, "Z", { valueOption.pendingValue().pos.z }) { z ->
            val cur = valueOption.pendingValue()
            valueOption.requestSet(cur.copy(pos = Vec3(cur.pos.x, cur.pos.y, z)))
        }
        yawField = InlineNumberField.floatField(placeholder, YAW_LABEL, { valueOption.pendingValue().yaw }) { yaw ->
            valueOption.requestSet(valueOption.pendingValue().copy(yaw = yaw))
        }
        pitchField = InlineNumberField.floatField(placeholder, PITCH_LABEL, { valueOption.pendingValue().pitch }) { pitch ->
            valueOption.requestSet(valueOption.pendingValue().copy(pitch = pitch))
        }
        syncFields()
        sampleButton = if (showSampleButton) {
            Button.builder(Component.translatable(USE_POS_KEY)) {
                MatchFieldPlayerSamples.spawnWithFacing()?.let { spawn ->
                    valueOption.requestSet(
                        PositionAndFacing(Vec3(spawn.x, spawn.y, spawn.z), spawn.yaw, spawn.pitch),
                    )
                    syncFields()
                }
            }.bounds(0, 0, SAMPLE_BUTTON_WIDTH, MIN_ROW_HEIGHT).build()
        } else {
            null
        }
    }

    private fun activeField(): InlineNumberField? =
        listOf(xField, yField, zField, yawField, pitchField).firstOrNull { it.isFieldFocused() }

    private fun syncFields() {
        xField.syncFromValue()
        yField.syncFromValue()
        zField.syncFromValue()
        yawField.syncFromValue()
        pitchField.syncFromValue()
    }

    private fun isEditing(): Boolean =
        compact || isHovered || xField.isFieldFocused() || yField.isFieldFocused() ||
            zField.isFieldFocused() || yawField.isFieldFocused() || pitchField.isFieldFocused()

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
        val fieldWidth = if (compact) COMPACT_FIELD_WIDTH else FIELD_WIDTH
        val slices = layoutSlices(dim, fieldWidth)
        val rowHighlight = isHovered || isFocused
        xField.dimension = slices[0]
        yField.dimension = slices[1]
        zField.dimension = slices[2]
        yawField.dimension = slices[3]
        pitchField.dimension = slices[4]
        xField.rowHighlight = rowHighlight
        yField.rowHighlight = rowHighlight
        zField.rowHighlight = rowHighlight
        yawField.rowHighlight = rowHighlight
        pitchField.rowHighlight = rowHighlight
        xField.extractRenderState(graphics, mouseX, mouseY, delta)
        yField.extractRenderState(graphics, mouseX, mouseY, delta)
        zField.extractRenderState(graphics, mouseX, mouseY, delta)
        yawField.extractRenderState(graphics, mouseX, mouseY, delta)
        pitchField.extractRenderState(graphics, mouseX, mouseY, delta)
        sampleButton?.let { btn ->
            val btnDim = slices[5]
            btn.setPosition(btnDim.x(), btnDim.y())
            btn.setWidth(btnDim.width())
            btn.setHeight(btnDim.height())
            btn.extractRenderState(graphics, mouseX, mouseY, delta)
        }
    }

    private fun layoutSlices(dim: Dimension<Int>, fieldWidth: Int): List<Dimension<Int>> {
        val y = dim.y()
        val h = dim.height().coerceAtLeast(MIN_ROW_HEIGHT)
        var right = dim.xLimit() - xPadding
        val slices = mutableListOf<Dimension<Int>>()

        if (showSampleButton) {
            right -= SAMPLE_BUTTON_WIDTH
            slices.add(Dimension.ofInt(right, y, SAMPLE_BUTTON_WIDTH, h))
            right -= FIELD_GAP
        }

        repeat(5) {
            right -= fieldWidth
            slices.add(0, Dimension.ofInt(right, y, fieldWidth, h))
            right -= FIELD_GAP
        }
        return slices
    }

    override fun getHoveredControlWidth(): Int {
        if (!isEditing()) {
            return textRenderer.width(control.formatValue().string).coerceAtMost(controlAreaWidth())
        }
        val fieldWidth = if (compact) COMPACT_FIELD_WIDTH else FIELD_WIDTH
        var w = fieldWidth * 5 + FIELD_GAP * 4
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
        yawField.unfocus()
        pitchField.unfocus()
        sampleButton?.isFocused = false
    }

    override fun isFocused(): Boolean =
        focused || xField.isFocused || yField.isFocused || zField.isFocused ||
            yawField.isFocused || pitchField.isFocused || sampleButton?.isFocused == true

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        if (!isAvailable || !dimension.isPointInside(event.x().toInt(), event.y().toInt())) {
            unfocus()
            return false
        }
        if (sampleButton?.mouseClicked(event, doubled) == true) return true
        if (delegateFieldClick(pitchField, event, doubled)) return true
        if (delegateFieldClick(yawField, event, doubled)) return true
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
        if (field !== yawField) yawField.unfocus()
        if (field !== pitchField) pitchField.unfocus()
        focused = true
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean =
        sampleButton?.mouseReleased(event) == true ||
            pitchField.mouseReleased(event) ||
            yawField.mouseReleased(event) ||
            zField.mouseReleased(event) ||
            yField.mouseReleased(event) ||
            xField.mouseReleased(event)

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean =
        sampleButton?.mouseDragged(event, deltaX, deltaY) == true ||
            pitchField.mouseDragged(event, deltaX, deltaY) ||
            yawField.mouseDragged(event, deltaX, deltaY) ||
            zField.mouseDragged(event, deltaX, deltaY) ||
            yField.mouseDragged(event, deltaX, deltaY) ||
            xField.mouseDragged(event, deltaX, deltaY)

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean =
        sampleButton?.mouseScrolled(mouseX, mouseY, horizontal, vertical) == true ||
            pitchField.mouseScrolled(mouseX, mouseY, horizontal, vertical) ||
            yawField.mouseScrolled(mouseX, mouseY, horizontal, vertical) ||
            zField.mouseScrolled(mouseX, mouseY, horizontal, vertical) ||
            yField.mouseScrolled(mouseX, mouseY, horizontal, vertical) ||
            xField.mouseScrolled(mouseX, mouseY, horizontal, vertical)

    override fun hasActiveInlineField(): Boolean = activeField() != null

    override fun handleKeyPressed(event: KeyEvent): Boolean = keyPressed(event)

    override fun handleCharTyped(event: CharacterEvent): Boolean = charTyped(event)

    override fun keyPressed(event: KeyEvent): Boolean {
        activeField()?.let { if (it.keyPressed(event)) return true }
        return sampleButton?.keyPressed(event) == true ||
            pitchField.keyPressed(event) ||
            yawField.keyPressed(event) ||
            zField.keyPressed(event) ||
            yField.keyPressed(event) ||
            xField.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        activeField()?.let { if (it.charTyped(event)) return true }
        return pitchField.charTyped(event) || yawField.charTyped(event) || zField.charTyped(event) ||
            yField.charTyped(event) || xField.charTyped(event)
    }

    companion object {
        private const val YAW_LABEL = "Yaw"
        private const val PITCH_LABEL = "Pitch"
        private const val USE_POS_KEY = "screen.nmbct-football.field.use_current_pos_facing"
        private const val FIELD_WIDTH = 72
        private const val COMPACT_FIELD_WIDTH = 58
        private const val FIELD_GAP = 3
        private const val SAMPLE_BUTTON_WIDTH = 100
        private const val MIN_ROW_HEIGHT = 18
    }
}
