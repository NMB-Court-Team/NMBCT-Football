package net.astrorbits.football.client.util

import net.minecraft.client.Minecraft

object FootballHudVisibility {
    /** F3 调试叠加层是否打开（不含 F3 配置里设为「始终显示」的条目）。 */
    fun isDebugOverlayOpen(client: Minecraft): Boolean =
        client.debugEntries.isOverlayVisible()
}
