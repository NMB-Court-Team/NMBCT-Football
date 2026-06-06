package net.astrorbits.football.mixin;

import net.astrorbits.football.match.ThrowInSetPieceFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public class ThrowInMovementFreezeMixin {
    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true, name = "input")
    private Vec3 nmbctFootball$freezeThrowInTravel(Vec3 input) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide() || !(self instanceof ServerPlayer serverPlayer)) {
            return input;
        }
        if (ThrowInSetPieceFlow.INSTANCE.isMovementFrozen(serverPlayer)) {
            return Vec3.ZERO;
        }
        return input;
    }
}
