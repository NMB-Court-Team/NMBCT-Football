package net.astrorbits.football.client

import net.astrorbits.football.Football
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3

/**
 * 本地带球 session 跟踪：用于视野外足球方位 HUD。
 */
object DribbleBallIndicatorClient {
    var activeFootballId: Int = -1
        private set

    fun onDribbleHold() {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val level = client.level ?: return
        val football = level.getEntitiesOfClass(Football::class.java, player.boundingBox.inflate(8.0))
            .minByOrNull { it.distanceToSqr(player) }
        activeFootballId = football?.id ?: -1
    }

    fun onDribbleEnd() {
        activeFootballId = -1
    }

    fun activeBallCenter(): Vec3? {
        if (activeFootballId < 0) {
            return null
        }
        val level = Minecraft.getInstance().level ?: return null
        val entity = level.getEntity(activeFootballId) as? Football ?: run {
            activeFootballId = -1
            return null
        }
        return entity.position().add(0.0, entity.bbHeight * 0.5, 0.0)
    }
}
