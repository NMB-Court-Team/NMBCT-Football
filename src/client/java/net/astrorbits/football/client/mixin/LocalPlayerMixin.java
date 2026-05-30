package net.astrorbits.football.client.mixin;

import net.astrorbits.football.client.key.LookAroundClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends Entity {
    public LocalPlayerMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void nmbctFootball$lookAroundHead(CallbackInfo ci) {
        LookAroundClient.onAiStepHead((LocalPlayer) (Object) this);
    }

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void nmbctFootball$lookAroundReturn(CallbackInfo ci) {
        LookAroundClient.onAiStepReturn((LocalPlayer) (Object) this);
    }

    @Override
    public void turn(double xo, double yo) {
        super.turn(xo, yo);
        LookAroundClient.onTurnApplied((LocalPlayer) (Object) this);
    }
}
