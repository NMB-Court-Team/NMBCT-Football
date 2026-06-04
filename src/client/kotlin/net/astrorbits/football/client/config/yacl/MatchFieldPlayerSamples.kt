package net.astrorbits.football.client.config.yacl

import net.astrorbits.football.match.KickPosition
import net.astrorbits.football.match.SpawnPosition
import net.minecraft.client.Minecraft

/** 从本地玩家读取场地配置用的坐标样本。 */
object MatchFieldPlayerSamples {
    fun position(): KickPosition? = withPlayer { player ->
        KickPosition(player.x, player.y, player.z)
    }

    fun spawnWithFacing(): SpawnPosition? = withPlayer { player ->
        SpawnPosition(
            x = player.x,
            y = player.y,
            z = player.z,
            yaw = player.yRot,
            pitch = player.xRot,
        )
    }

    fun sidelineCoord(axis: String): Double? = withPlayer { player ->
        when (axis.lowercase()) {
            "x" -> player.z
            else -> player.x
        }
    }

    fun <T> withPlayer(block: (net.minecraft.client.player.LocalPlayer) -> T): T? {
        val player = Minecraft.getInstance().player ?: return null
        return block(player)
    }
}
