package net.astrorbits.football.mixin

import net.astrorbits.football.input.SlideTackleStateAccess
import net.minecraft.world.entity.LivingEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(LivingEntity::class)
abstract class SlideTackleSwimAmountMixin {
    @Shadow
    private var swimAmount: Float = 0.0f

    @Shadow
    private var swimAmountO: Float = 0.0f

    @Unique
    private var nmbctFootballWasSliding: Boolean = false

    @Inject(method = ["tick"], at = [At("HEAD")])
    private fun nmbctFootball_forceSwimAmountSnap(ci: CallbackInfo) {
        val slideState = this as? SlideTackleStateAccess ?: return
        val sliding = slideState.nmbctFootball_isSlideTackling()
        if (sliding) {
            // 进入滑铲：上一帧与当前帧都钉满，避免进入时插值过渡。
            swimAmount = 1.0f
            swimAmountO = 1.0f
            nmbctFootballWasSliding = true
            return
        }
        if (nmbctFootballWasSliding) {
            // 退出滑铲：上一帧与当前帧都清零，避免退出时平滑回弹。
            swimAmount = 0.0f
            swimAmountO = 0.0f
            nmbctFootballWasSliding = false
        }
    }

    @Inject(method = ["getSwimAmount"], at = [At("HEAD")], cancellable = true)
    private fun nmbctFootball_forceInstantSwimPose(partialTick: Float, cir: CallbackInfoReturnable<Float>) {
        val slideState = this as? SlideTackleStateAccess ?: return
        if (slideState.nmbctFootball_isSlideTackling()) {
            // 滑铲时强制游泳姿态权重为 1。
            cir.returnValue = 1.0f
            return
        }
        if (nmbctFootballWasSliding) {
            // 刚退出滑铲的这一帧强制为 0，彻底消掉收起插值。
            cir.returnValue = 0.0f
            nmbctFootballWasSliding = false
        }
    }
}
