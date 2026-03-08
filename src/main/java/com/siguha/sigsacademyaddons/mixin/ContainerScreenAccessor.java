package com.siguha.sigsacademyaddons.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {

    @Accessor("hoveredSlot")
    Slot getHoveredSlot();

    @Accessor("topPos")
    int getTopPos();

    @Accessor("leftPos")
    int getLeftPos();

    @Accessor("imageWidth")
    int getImageWidth();

    @Accessor("imageHeight")
    int getImageHeight();
}
