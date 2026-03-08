package com.siguha.sigsacademyaddons.mixin;

import com.siguha.sigsacademyaddons.handler.ScreenInterceptor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class ContainerScreenClickMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void saa$beforeMouseClicked(double mouseX, double mouseY, int button,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (!ScreenInterceptor.shouldAllowContainerClick(
                (AbstractContainerScreen<?>) (Object) this, button)) {
            cir.setReturnValue(true);
        }
    }
}
