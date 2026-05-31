package net.astrorbits.football.mixin

import net.astrorbits.football.input.SlideTackleStateAccess
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.ModifyVariable
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Player::class)
abstract class PlayerSlidePoseMixin : SlideTackleStateAccess {
    @Unique
    private var nmbctFootballSlideTackling: Boolean = false

    override fun nmbctFootball_isSlideTackling(): Boolean = nmbctFootballSlideTackling

    override fun nmbctFootball_setSlideTackling(sliding: Boolean) {
        nmbctFootballSlideTackling = sliding
    }

    @Inject(method = ["updatePlayerPose"], at = [At("HEAD")], cancellable = true)
    private fun nmbctFootball_applySlidePose(ci: CallbackInfo) {
        if (!nmbctFootballSlideTackling) {
            return
        }
        val self = this as Player
        if (self.pose != Pose.SWIMMING) {
            self.setPose(Pose.SWIMMING)
        }
        ci.cancel()
    }

    @ModifyVariable(method = ["travel"], at = At("HEAD"), argsOnly = true)
    private fun nmbctFootball_lockTravelInputWhileSliding(input: Vec3): Vec3 {
        if (!nmbctFootballSlideTackling) {
            return input
        }
        // 滑铲期间屏蔽水平输入，移动仅由滑铲会话的固定方向驱动。
        return Vec3(0.0, input.y, 0.0)
    }
}
