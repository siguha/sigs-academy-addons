package com.siguha.sigsacademyaddons.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.siguha.sigsacademyaddons.SigsAcademyAddonsClient;
import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.feature.safari.HuntEntityTracker;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// forces spectral glow on pokemon matching active hunts (controlled by safariQuestMonGlow)
@Mixin(Entity.class)
public abstract class EntityGlowMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true)
    private void sig_isCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof PokemonEntity)) return;
        if (cir.getReturnValue()) return;

        HudConfig config = SigsAcademyAddonsClient.getHudConfig();
        if (config == null || !config.isSafariQuestMonGlow()) return;

        HuntEntityTracker tracker = SigsAcademyAddonsClient.getHuntEntityTracker();
        if (tracker == null) return;

        Entity self = (Entity) (Object) this;
        if (tracker.isMatched(self.getId())) {
            cir.setReturnValue(true);
        }
    }
}
