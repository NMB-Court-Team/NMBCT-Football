package net.astrorbits.football.client

import net.astrorbits.football.input.SlideTacklePoseTuning
import net.minecraft.client.model.player.PlayerModel
import kotlin.jvm.JvmStatic

object SlideTacklePoseClient {
    @JvmStatic
    fun isPlayerSliding(entityId: Int): Boolean = SlideTackleStateClient.isSliding(entityId)

    @JvmStatic
    fun applySlidePose(model: PlayerModel) {
        model.root().xRot = SlideTacklePoseTuning.rootX
        model.root().yRot = SlideTacklePoseTuning.rootY
        model.root().zRot = SlideTacklePoseTuning.rootZ
        model.root().x += SlideTacklePoseTuning.rootOffsetX
        model.root().y += SlideTacklePoseTuning.rootOffsetY
        model.root().z += SlideTacklePoseTuning.rootOffsetZ

        model.head.xRot = SlideTacklePoseTuning.headX
        model.head.yRot = SlideTacklePoseTuning.headY
        model.head.zRot = SlideTacklePoseTuning.headZ
        model.leftArm.xRot = SlideTacklePoseTuning.leftArmX
        model.leftArm.yRot = SlideTacklePoseTuning.leftArmY
        model.leftArm.zRot = SlideTacklePoseTuning.leftArmZ
        model.rightArm.xRot = SlideTacklePoseTuning.rightArmX
        model.rightArm.yRot = SlideTacklePoseTuning.rightArmY
        model.rightArm.zRot = SlideTacklePoseTuning.rightArmZ

        model.leftLeg.xRot = SlideTacklePoseTuning.leftLegX
        model.leftLeg.yRot = SlideTacklePoseTuning.leftLegY
        model.leftLeg.zRot = SlideTacklePoseTuning.leftLegZ
        model.rightLeg.xRot = SlideTacklePoseTuning.rightLegX
        model.rightLeg.yRot = SlideTacklePoseTuning.rightLegY
        model.rightLeg.zRot = SlideTacklePoseTuning.rightLegZ
    }
}
