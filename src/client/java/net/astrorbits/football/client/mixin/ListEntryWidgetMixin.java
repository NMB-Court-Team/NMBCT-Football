package net.astrorbits.football.client.mixin;

import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.controllers.ListEntryWidget;
import net.astrorbits.football.client.config.yacl.controller.InlineFieldKeyboardHost;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 列表项内联编辑时，将键盘事件转发给 {@link InlineFieldKeyboardHost}。
 */
@Mixin(ListEntryWidget.class)
public abstract class ListEntryWidgetMixin {

    @Shadow @Final private AbstractWidget entryWidget;

    @Shadow public abstract void setFocused(GuiEventListener focused);

    @Inject(method = "mouseClicked", at = @At("TAIL"))
    private void nmbct$focusEntryWhenInlineFieldActive(
        MouseButtonEvent mouseButtonEvent,
        boolean doubleClick,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (entryWidget instanceof InlineFieldKeyboardHost host && host.hasActiveInlineField()) {
            setFocused(entryWidget);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void nmbct$forwardKeyToInlineFields(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        if (entryWidget instanceof InlineFieldKeyboardHost host && host.hasActiveInlineField()) {
            if (host.handleKeyPressed(keyEvent)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void nmbct$forwardCharToInlineFields(CharacterEvent characterEvent, CallbackInfoReturnable<Boolean> cir) {
        if (entryWidget instanceof InlineFieldKeyboardHost host && host.hasActiveInlineField()) {
            if (host.handleCharTyped(characterEvent)) {
                cir.setReturnValue(true);
            }
        }
    }
}
