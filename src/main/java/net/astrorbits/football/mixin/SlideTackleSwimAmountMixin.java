package net.astrorbits.football.mixin;

import net.astrorbits.football.mixinhelper.SlideTackleStateAccess;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class SlideTackleSwimAmountMixin {
    @Shadow
    private float swimAmount;

    @Shadow
    private float swimAmountO;

    @Unique
    private boolean nmbctFootball$wasSliding;

    @Inject(method = "tick", at = @At("HEAD"))
    private void nmbctFootball$forceSwimAmountSnap(CallbackInfo ci) {
        if (!(this instanceof SlideTackleStateAccess slideState)) {
            return;
        }
        boolean sliding = slideState.nmbctFootball$isSlideTackling();
        if (sliding) {
            swimAmount = 1.0f;
            swimAmountO = 1.0f;
            nmbctFootball$wasSliding = true;
            return;
        }
        if (nmbctFootball$wasSliding) {
            swimAmount = 0.0f;
            swimAmountO = 0.0f;
            nmbctFootball$wasSliding = false;
        }
    }

    @Inject(method = "getSwimAmount", at = @At("HEAD"), cancellable = true)
    private void nmbctFootball$forceInstantSwimPose(float a /* partialTick */, CallbackInfoReturnable<Float> cir) {
        if (!(this instanceof SlideTackleStateAccess slideState)) {
            return;
        }
        if (slideState.nmbctFootball$isSlideTackling()) {
            cir.setReturnValue(1.0f);
            return;
        }
        if (nmbctFootball$wasSliding) {
            cir.setReturnValue(0.0f);
            nmbctFootball$wasSliding = false;
        }
    }
}
