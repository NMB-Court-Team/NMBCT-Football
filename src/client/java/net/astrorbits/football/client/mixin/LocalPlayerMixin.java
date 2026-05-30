package net.astrorbits.football.client.mixin;

import net.astrorbits.football.client.key.LookAroundClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void nmbctFootball$lookAroundHead(CallbackInfo ci) {
        LookAroundClient.onAiStepHead((LocalPlayer) (Object) this);
    }

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void nmbctFootball$lookAroundReturn(CallbackInfo ci) {
        LookAroundClient.onAiStepReturn((LocalPlayer) (Object) this);
    }
}
