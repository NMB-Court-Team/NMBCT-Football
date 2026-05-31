package net.astrorbits.football.mixin

import net.astrorbits.football.input.SlideTackleStateAccess
import net.minecraft.world.entity.LivingEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(LivingEntity::class)
abstract class SlideTackleJumpBlockMixin {
    @Inject(method = ["jumpFromGround"], at = [At("HEAD")], cancellable = true)
    private fun nmbctFootball_blockJumpWhileSliding(ci: CallbackInfo) {
        val slideState = this as? SlideTackleStateAccess ?: return
        if (slideState.nmbctFootball_isSlideTackling()) {
            ci.cancel()
        }
    }
}
