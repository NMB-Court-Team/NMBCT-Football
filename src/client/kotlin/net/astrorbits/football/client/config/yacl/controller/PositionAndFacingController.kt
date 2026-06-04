package net.astrorbits.football.client.config.yacl.controller

import dev.isxander.yacl3.api.Controller
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.utils.Dimension
import dev.isxander.yacl3.gui.AbstractWidget
import dev.isxander.yacl3.gui.YACLScreen
import net.minecraft.network.chat.Component

class PositionAndFacingController(
    private val valueOption: Option<PositionAndFacing>,
    val showSampleButton: Boolean = true,
    val compact: Boolean = false,
) : Controller<PositionAndFacing> {
    override fun option(): Option<PositionAndFacing> = valueOption

    override fun formatValue(): Component {
        val v = valueOption.pendingValue()
        return Component.literal(
            "%.1f, %.1f, %.1f · %.0f° / %.0f°".format(
                v.pos.x,
                v.pos.y,
                v.pos.z,
                v.yaw,
                v.pitch,
            ),
        )
    }

    override fun provideWidget(screen: YACLScreen, widgetDimension: Dimension<Int>): AbstractWidget =
        PositionAndFacingControllerElement(this, screen, widgetDimension)

    companion object {
        fun create(
            option: Option<PositionAndFacing>,
            showSampleButton: Boolean = true,
            compact: Boolean = false,
        ): PositionAndFacingController = PositionAndFacingController(option, showSampleButton, compact)
    }
}
