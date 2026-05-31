package net.astrorbits.football.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 冒险模式下隐藏血量、饱食度、经验条和快捷栏。
 */
@Mixin(net.minecraft.client.gui.Gui.class)
public abstract class AdventureHideHudMixin {

    @Inject(method = "extractHearts", at = @At("HEAD"), cancellable = true)
    private void hideHeartsInAdventure(CallbackInfo ci) {
        if (isAdventure()) ci.cancel();
    }

    @Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
    private void hideFoodInAdventure(CallbackInfo ci) {
        if (isAdventure()) ci.cancel();
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void hideHotbarInAdventure(CallbackInfo ci) {
        if (isAdventure()) ci.cancel();
    }

    @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
    private void hideItemHotbarInAdventure(CallbackInfo ci) {
        if (isAdventure()) ci.cancel();
    }

    private static boolean isAdventure() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null
            && client.gameMode != null
            && client.gameMode.getPlayerMode() == GameType.ADVENTURE;
    }
}
