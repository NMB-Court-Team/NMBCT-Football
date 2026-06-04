package net.astrorbits.football.client.config.yacl.controller

import dev.isxander.yacl3.api.Controller
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.utils.Dimension
import dev.isxander.yacl3.gui.AbstractWidget
import dev.isxander.yacl3.gui.YACLScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3

class PositionController(
    private val posOption: Option<Vec3>,
    val showSampleButton: Boolean = true,
    val compact: Boolean = false,
) : Controller<Vec3> {
    override fun option(): Option<Vec3> = posOption

    override fun formatValue(): Component {
        val p = posOption.pendingValue()
        return Component.literal("%.2f, %.2f, %.2f".format(p.x, p.y, p.z))
    }

    override fun provideWidget(screen: YACLScreen, widgetDimension: Dimension<Int>): AbstractWidget =
        PositionControllerElement(this, screen, widgetDimension)

    companion object {
        fun create(
            option: Option<Vec3>,
            showSampleButton: Boolean = true,
            compact: Boolean = false,
        ): PositionController = PositionController(option, showSampleButton, compact)
    }
}
