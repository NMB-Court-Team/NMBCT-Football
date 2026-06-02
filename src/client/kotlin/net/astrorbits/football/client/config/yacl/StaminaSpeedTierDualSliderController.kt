package net.astrorbits.football.client.config.yacl

import dev.isxander.yacl3.api.Controller
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder
import dev.isxander.yacl3.api.utils.Dimension
import dev.isxander.yacl3.gui.AbstractWidget
import dev.isxander.yacl3.gui.YACLScreen
import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.config.server.StaminaSpeedTier
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * 单条移速档位：体力比例 + 移速倍率双滑条（用于 [dev.isxander.yacl3.api.ListOption] 每一项）。
 */
class StaminaSpeedTierDualSliderController(
    private val tierOption: Option<StaminaSpeedTier>,
) : Controller<StaminaSpeedTier> {
    override fun option(): Option<StaminaSpeedTier> = tierOption

    override fun formatValue(): Component {
        val value = tierOption.pendingValue()
        val pct = (value.staminaFraction * 100f).toInt()
        return Component.literal("$pct% → ${SPEED_MULTIPLIER_FORMAT.format(value.speedMultiplier)}")
    }

    override fun provideWidget(screen: YACLScreen, widgetDimension: Dimension<Int>): AbstractWidget =
        DualSliderWidget(tierOption, screen, widgetDimension)

    private class DualSliderWidget(
        private val tierOption: Option<StaminaSpeedTier>,
        private val screen: YACLScreen,
        dim: Dimension<Int>,
    ) : AbstractWidget(dim) {
        private val fractionWidget: AbstractWidget
        private val multiplierWidget: AbstractWidget

        init {
            val tier = tierOption.pendingValue()

            val fractionOpt = Option.createBuilder<Float>()
                .name(Component.translatable("$TIER_KEY.stamina_fraction"))
                .description(OptionDescription.of(Component.translatable("$TIER_KEY.stamina_fraction.desc")))
                .binding(
                    tier.staminaFraction,
                    { tierOption.pendingValue().staminaFraction },
                    { v: Float ->
                        val cur = tierOption.pendingValue()
                        tierOption.requestSet(cur.copy(staminaFraction = v.coerceIn(0f, 1f)))
                    },
                )
                .customController { opt ->
                    FloatSliderControllerBuilder.create(opt)
                        .range(0f, 1f)
                        .step(0.01f)
                        .build()
                }
                .build()

            val multiplierOpt = Option.createBuilder<Float>()
                .name(Component.translatable("$TIER_KEY.speed_multiplier"))
                .description(OptionDescription.of(Component.translatable("$TIER_KEY.speed_multiplier.desc")))
                .binding(
                    tier.speedMultiplier,
                    { tierOption.pendingValue().speedMultiplier },
                    { v: Float ->
                        val cur = tierOption.pendingValue()
                        tierOption.requestSet(cur.copy(speedMultiplier = v.coerceIn(0.1f, 2f)))
                    },
                )
                .customController { opt ->
                    FloatSliderControllerBuilder.create(opt)
                        .range(0.1f, 2f)
                        .step(0.05f)
                        .formatValue { value -> Component.literal(SPEED_MULTIPLIER_FORMAT.format(value)) }
                        .build()
                }
                .build()

            fractionWidget = fractionOpt.controller().provideWidget(screen, columnDimension(dim, 0))
            multiplierWidget = multiplierOpt.controller().provideWidget(screen, columnDimension(dim, 1))
        }

        override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            layoutChildren()
            fractionWidget.extractRenderState(graphics, mouseX, mouseY, delta)
            multiplierWidget.extractRenderState(graphics, mouseX, mouseY, delta)
        }

        override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean =
            fractionWidget.mouseClicked(event, doubled) || multiplierWidget.mouseClicked(event, doubled)

        override fun mouseReleased(event: MouseButtonEvent): Boolean =
            fractionWidget.mouseReleased(event) || multiplierWidget.mouseReleased(event)

        override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean =
            fractionWidget.mouseDragged(event, deltaX, deltaY) || multiplierWidget.mouseDragged(event, deltaX, deltaY)

        override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean =
            fractionWidget.mouseScrolled(mouseX, mouseY, horizontal, vertical) ||
                multiplierWidget.mouseScrolled(mouseX, mouseY, horizontal, vertical)

        override fun keyPressed(event: KeyEvent): Boolean =
            fractionWidget.keyPressed(event) || multiplierWidget.keyPressed(event)

        override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean =
            fractionWidget.isMouseOver(mouseX, mouseY) || multiplierWidget.isMouseOver(mouseX, mouseY)

        override fun isFocused(): Boolean =
            fractionWidget.isFocused || multiplierWidget.isFocused

        override fun setFocused(focused: Boolean) {
            if (!focused) {
                fractionWidget.setFocused(false)
                multiplierWidget.setFocused(false)
            }
        }

        private fun layoutChildren() {
            @Suppress("UNCHECKED_CAST")
            val dim = getDimension() as Dimension<Int>
            fractionWidget.setDimension(columnDimension(dim, 0))
            multiplierWidget.setDimension(columnDimension(dim, 1))
        }

        /**
         * YACL 列表项高度被 [dev.isxander.yacl3.gui.controllers.ListEntryWidget] 限制在约 20px，
         * 因此采用左右并排，使每个滑条占满行高而非上下各一半。
         */
        private fun columnDimension(dim: Dimension<Int>, column: Int): Dimension<Int> {
            val gap = COLUMN_GAP
            val colWidth = ((dim.width() - gap) / 2).coerceAtLeast(MIN_COLUMN_WIDTH)
            val x = dim.x() + column * (colWidth + gap)
            return Dimension.ofInt(x, dim.y(), colWidth, dim.height().coerceAtLeast(MIN_ROW_HEIGHT))
        }
    }

    companion object {
        private const val SPEED_MULTIPLIER_FORMAT = "%.2f"
        private const val TIER_KEY = "yacl3.config.${NMBCTFootball.MOD_ID}.server.stamina_tier"
        private const val COLUMN_GAP = 6
        private const val MIN_COLUMN_WIDTH = 80
        /** 与 ListEntryWidget 上限一致，占满分配行高 */
        private const val MIN_ROW_HEIGHT = 18

        fun create(option: Option<StaminaSpeedTier>): StaminaSpeedTierDualSliderController =
            StaminaSpeedTierDualSliderController(option)
    }
}
