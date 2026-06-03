package net.astrorbits.football.client.config.yacl

import dev.isxander.yacl3.api.Controller
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder
import dev.isxander.yacl3.api.utils.Dimension
import dev.isxander.yacl3.gui.AbstractWidget
import dev.isxander.yacl3.gui.YACLScreen
import net.astrorbits.football.match.SpawnPosition
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/** 出生点列表项：坐标 + 朝向 +「取当前位置」按钮。 */
class SpawnPositionListController(
    private val posOption: Option<SpawnPosition>,
) : Controller<SpawnPosition> {
    override fun option(): Option<SpawnPosition> = posOption

    override fun formatValue(): Component {
        val p = posOption.pendingValue()
        return Component.literal("%.1f, %.1f, %.1f".format(p.x, p.y, p.z))
    }

    override fun provideWidget(screen: YACLScreen, widgetDimension: Dimension<Int>): AbstractWidget =
        Widget(posOption, screen, widgetDimension)

    private class Widget(
        private val posOption: Option<SpawnPosition>,
        private val screen: YACLScreen,
        dim: Dimension<Int>,
    ) : AbstractWidget(dim) {
        private val sliderWidgets: List<AbstractWidget>
        private val usePosButton: Button

        init {
            val p = posOption.pendingValue()
            val options = listOf(
                slider("$KEY.x", p.x, { it.x }, { cur, v -> cur.copy(x = v) }, -3000.0, 3000.0),
                slider("$KEY.y", p.y, { it.y }, { cur, v -> cur.copy(y = v) }, -64.0, 320.0),
                slider("$KEY.z", p.z, { it.z }, { cur, v -> cur.copy(z = v) }, -3000.0, 3000.0),
                floatSlider("$KEY.yaw", p.yaw, { it.yaw }, { cur, v -> cur.copy(yaw = v) }, -180f, 180f),
                floatSlider("$KEY.pitch", p.pitch, { it.pitch }, { cur, v -> cur.copy(pitch = v) }, -90f, 90f),
            )
            sliderWidgets = options.mapIndexed { index, opt ->
                opt.controller().provideWidget(screen, rowDimension(dim, index, SLIDER_ROWS))
            }
            usePosButton = Button.builder(Component.translatable(USE_POS_KEY)) {
                MatchFieldPlayerSamples.spawnWithFacing()?.let { posOption.requestSet(it) }
            }.bounds(dim.x(), dim.y(), dim.width(), BUTTON_ROW_HEIGHT).build()
        }

        private fun slider(
            nameKey: String,
            default: Double,
            getter: (SpawnPosition) -> Double,
            setter: (SpawnPosition, Double) -> SpawnPosition,
            min: Double,
            max: Double,
        ): Option<Double> = Option.createBuilder<Double>()
            .name(Component.translatable(nameKey))
            .description(OptionDescription.of(Component.translatable(MatchYaclDesc.desc(nameKey))))
            .binding(
                default,
                { getter(posOption.pendingValue()) },
                { v -> posOption.requestSet(setter(posOption.pendingValue(), v.coerceIn(min, max))) },
            )
            .customController { opt ->
                DoubleSliderControllerBuilder.create(opt).range(min, max).step(0.1).build()
            }
            .build()

        private fun floatSlider(
            nameKey: String,
            default: Float,
            getter: (SpawnPosition) -> Float,
            setter: (SpawnPosition, Float) -> SpawnPosition,
            min: Float,
            max: Float,
        ): Option<Float> = Option.createBuilder<Float>()
            .name(Component.translatable(nameKey))
            .description(OptionDescription.of(Component.translatable(MatchYaclDesc.desc(nameKey))))
            .binding(
                default,
                { getter(posOption.pendingValue()) },
                { v -> posOption.requestSet(setter(posOption.pendingValue(), v.coerceIn(min, max))) },
            )
            .customController { opt ->
                FloatSliderControllerBuilder.create(opt).range(min, max).step(1f).build()
            }
            .build()

        override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            layoutChildren()
            sliderWidgets.forEach { it.extractRenderState(graphics, mouseX, mouseY, delta) }
            usePosButton.extractRenderState(graphics, mouseX, mouseY, delta)
        }

        override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean =
            usePosButton.mouseClicked(event, doubled) ||
                sliderWidgets.any { it.mouseClicked(event, doubled) }

        override fun mouseReleased(event: MouseButtonEvent): Boolean =
            usePosButton.mouseReleased(event) ||
                sliderWidgets.any { it.mouseReleased(event) }

        override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean =
            usePosButton.mouseDragged(event, deltaX, deltaY) ||
                sliderWidgets.any { it.mouseDragged(event, deltaX, deltaY) }

        override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean =
            usePosButton.mouseScrolled(mouseX, mouseY, horizontal, vertical) ||
                sliderWidgets.any { it.mouseScrolled(mouseX, mouseY, horizontal, vertical) }

        override fun keyPressed(event: KeyEvent): Boolean =
            usePosButton.keyPressed(event) ||
                sliderWidgets.any { it.keyPressed(event) }

        override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean =
            usePosButton.isMouseOver(mouseX, mouseY) ||
                sliderWidgets.any { it.isMouseOver(mouseX, mouseY) }

        override fun isFocused(): Boolean =
            usePosButton.isFocused || sliderWidgets.any { it.isFocused }

        override fun setFocused(focused: Boolean) {
            if (!focused) {
                usePosButton.isFocused = false
                sliderWidgets.forEach { it.setFocused(false) }
            }
        }

        private fun layoutChildren() {
            @Suppress("UNCHECKED_CAST")
            val dim = getDimension() as Dimension<Int>
            sliderWidgets.forEachIndexed { index, widget ->
                widget.setDimension(rowDimension(dim, index, SLIDER_ROWS))
            }
            usePosButton.setPosition(dim.x(), dim.y() + SLIDER_ROWS * rowHeight(dim))
            usePosButton.setWidth(dim.width())
            usePosButton.setHeight(BUTTON_ROW_HEIGHT)
        }

        private fun rowHeight(dim: Dimension<Int>): Int =
            (dim.height() / ROW_COUNT).coerceAtLeast(MIN_ROW_HEIGHT)

        private fun rowDimension(dim: Dimension<Int>, row: Int, totalSliderRows: Int): Dimension<Int> {
            val rowHeight = rowHeight(dim)
            return Dimension.ofInt(dim.x(), dim.y() + row * rowHeight, dim.width(), rowHeight)
        }
    }

    companion object {
        private const val KEY = "yacl3.config.nmbct-football.match.field.spawn_pos"
        private const val USE_POS_KEY = "screen.nmbct-football.field.use_current_pos"
        private const val SLIDER_ROWS = 5
        private const val ROW_COUNT = 6
        private const val MIN_ROW_HEIGHT = 18
        private const val BUTTON_ROW_HEIGHT = 20

        fun create(option: Option<SpawnPosition>): SpawnPositionListController =
            SpawnPositionListController(option)
    }
}
