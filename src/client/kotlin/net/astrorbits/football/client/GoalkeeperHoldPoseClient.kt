package net.astrorbits.football.client

import net.astrorbits.football.Football
import net.astrorbits.football.util.GoalkeeperHoldPoseUtil
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.world.entity.player.Player
import kotlin.jvm.JvmStatic

object GoalkeeperHoldPoseClient {
    private val holdingPlayerIds = mutableSetOf<Int>()

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            holdingPlayerIds.clear()
            val level = client.level ?: return@register
            val localPlayer = client.player
            for (player in level.players()) {
                if (isPlayerHoldingFootball(player)) {
                    holdingPlayerIds.add(player.id)
                    if (player === localPlayer) {
                        GoalkeeperHoldPoseUtil.alignBodyToHead(player)
                    }
                }
            }
        }
    }

    @JvmStatic
    fun isPlayerHoldingBall(entityId: Int): Boolean = holdingPlayerIds.contains(entityId)

    fun isPlayerHoldingFootball(player: Player): Boolean {
        return player.level().getEntitiesOfClass(
            Football::class.java,
            player.boundingBox.inflate(6.0),
        ).any { it.getHolderEntityId() == player.id }
    }

    @JvmStatic
    fun applyHoldArmPose(model: net.minecraft.client.model.player.PlayerModel) {
        model.rightArm.xRot = ARM_FORWARD_X
        model.leftArm.xRot = ARM_FORWARD_X
        model.rightArm.yRot = -ARM_SPREAD_Y
        model.leftArm.yRot = ARM_SPREAD_Y
        model.rightArm.zRot = 0f
        model.leftArm.zRot = 0f
    }

    private const val ARM_FORWARD_X = -1.22f
    private const val ARM_SPREAD_Y = 0.18f
}
