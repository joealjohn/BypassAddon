package com.example.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.render.Tracers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

@Mixin(Tracers.class)
public interface TracersAccessor {
    // Instead of accessing the field directly, invoke the contains method
    @Invoker("shouldBeIgnored")
    boolean invokeOriginalShouldBeIgnored(Entity entity);
}
