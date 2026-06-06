package net.astrorbits.football.client.mixin;

import net.astrorbits.football.client.SetPieceClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public class ThrowInMovementFreezeClientMixin {
    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true, name = "input")
    private Vec3 nmbctFootball$freezeThrowInTravelClient(Vec3 input) {
        Player self = (Player) (Object) this;
        if (!self.level().isClientSide() || !(self instanceof LocalPlayer localPlayer)) {
            return input;
        }
        if (SetPieceClient.INSTANCE.isMovementFrozen(localPlayer.getUUID())) {
            return Vec3.ZERO;
        }
        return input;
    }
}
