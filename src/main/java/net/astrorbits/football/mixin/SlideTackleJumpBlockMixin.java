package net.astrorbits.football.mixin;

import net.astrorbits.football.mixinhelper.SlideTackleStateAccess;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class SlideTackleJumpBlockMixin {
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void nmbctFootball$blockJumpWhileSliding(CallbackInfo ci) {
        if (!(this instanceof SlideTackleStateAccess slideState)) {
            return;
        }
        if (slideState.nmbctFootball$isSlideTackling()) {
            ci.cancel();
        }
    }
}
