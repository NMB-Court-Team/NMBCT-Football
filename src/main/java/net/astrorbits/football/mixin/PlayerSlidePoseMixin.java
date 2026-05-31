package net.astrorbits.football.mixin;

import net.astrorbits.football.mixinhelper.SlideTackleStateAccess;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerSlidePoseMixin implements SlideTackleStateAccess {
    @Unique
    private boolean nmbctFootball$slideTackling;

    @Override
    public boolean nmbctFootball$isSlideTackling() {
        return nmbctFootball$slideTackling;
    }

    @Override
    public void nmbctFootball$setSlideTackling(boolean sliding) {
        nmbctFootball$slideTackling = sliding;
    }

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void nmbctFootball$applySlidePose(CallbackInfo ci) {
        if (!nmbctFootball$slideTackling) {
            return;
        }
        Player self = (Player) (Object) this;
        if (self.getPose() != Pose.SWIMMING) {
            self.setPose(Pose.SWIMMING);
        }
        ci.cancel();
    }

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true, name = "input")
    private Vec3 nmbctFootball$lockTravelInputWhileSliding(Vec3 input) {
        if (!nmbctFootball$slideTackling) {
            return input;
        }
        return new Vec3(0.0, input.y, 0.0);
    }
}
