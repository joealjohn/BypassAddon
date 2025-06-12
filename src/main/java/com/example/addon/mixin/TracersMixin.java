package com.example.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Tracers;
import com.example.addon.modules.ModuleUnrestrictedESP;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Tracers.class)
public class TracersMixin {
    @Inject(method = "shouldBeIgnored", at = @At("HEAD"), cancellable = true)
    private void onShouldBeIgnored(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        ModuleUnrestrictedESP module = Modules.get().get(ModuleUnrestrictedESP.class);
        if (module != null && module.shouldBypassTracers()) {
            // If our module is active, don't ignore this entity
            // This effectively bypasses any Y-level checks
            cir.setReturnValue(false);
        }
    }
}
