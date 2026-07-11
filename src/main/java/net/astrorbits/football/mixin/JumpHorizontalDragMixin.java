package net.astrorbits.football.mixin;

import net.astrorbits.football.match.MatchState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 跳跃/滞空时水平速度减半（创造/旁观除外）；比赛未开始时不受限制。 */
@Mixin(LivingEntity.class)
public abstract class JumpHorizontalDragMixin {

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void nmbctFootball$dragHorizontalInAir(CallbackInfo ci) {
        if (!MatchState.INSTANCE.isDuringMatch()) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.onGround()) return;
        if (self instanceof Player player && (player.isCreative() || player.isSpectator())) return;
        Vec3 vel = self.getDeltaMovement();
        self.setDeltaMovement(vel.x * 0.5, vel.y, vel.z * 0.5);
    }
}
