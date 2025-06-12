package com.example.addon.modules;

import com.example.addon.BypassAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Set;

public class TracersExtended extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAppearance = settings.createGroup("Appearance");
    private final SettingGroup sgColors = settings.createGroup("Colors");

    // General settings
    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Select specific entities.")
        .defaultValue(EntityType.PLAYER)
        .build()
    );

    private final Setting<Boolean> ignoreSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Doesn't draw tracers to yourself.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Doesn't draw tracers to friends.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showInvisible = sgGeneral.add(new BoolSetting.Builder()
        .name("show-invisible")
        .description("Shows invisible entities.")
        .defaultValue(true)
        .build()
    );

    // Appearance settings
    private final Setting<Target> target = sgAppearance.add(new EnumSetting.Builder<Target>()
        .name("target")
        .description("What part of the entity to target.")
        .defaultValue(Target.Body)
        .build()
    );

    private final Setting<Boolean> stem = sgAppearance.add(new BoolSetting.Builder()
        .name("stem")
        .description("Draw a line through the center of the tracer target.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxDistanceSq = sgAppearance.add(new IntSetting.Builder()
        .name("max-distance")
        .description("Maximum squared distance for tracers to show.")
        .defaultValue(100000000) // Squared of 10000
        .min(0)
        .sliderMax(100000000)
        .build()
    );

    // Colors
    private final Setting<Boolean> distanceColors = sgColors.add(new BoolSetting.Builder()
        .name("distance-colors")
        .description("Changes color based on distance.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> playersColor = sgColors.add(new ColorSetting.Builder()
        .name("players-color")
        .description("The player's color.")
        .defaultValue(new SettingColor(205, 205, 205, 127))
        .visible(() -> !distanceColors.get())
        .build()
    );

    private final Setting<SettingColor> animalsColor = sgColors.add(new ColorSetting.Builder()
        .name("animals-color")
        .description("The animal's color.")
        .defaultValue(new SettingColor(145, 255, 145, 127))
        .visible(() -> !distanceColors.get())
        .build()
    );

    private final Setting<SettingColor> monstersColor = sgColors.add(new ColorSetting.Builder()
        .name("monsters-color")
        .description("The monster's color.")
        .defaultValue(new SettingColor(255, 145, 145, 127))
        .visible(() -> !distanceColors.get())
        .build()
    );

    private int count = 0;

    public TracersExtended() {
        super(BypassAddon.CATEGORY, "tracers-extended", "Renders tracers to entities with no distance limit.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        count = 0;

        if (mc.world == null || mc.player == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (shouldSkipEntity(entity)) continue;

            Color color = getEntityColor(entity);

            double x = entity.prevX + (entity.getX() - entity.prevX) * event.tickDelta;
            double y = entity.prevY + (entity.getY() - entity.prevY) * event.tickDelta;
            double z = entity.prevZ + (entity.getZ() - entity.prevZ) * event.tickDelta;

            double height = entity.getBoundingBox().maxY - entity.getBoundingBox().minY;
            if (target.get() == Target.Head) y += height;
            else if (target.get() == Target.Body) y += height / 2;

            event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, x, y, z, color);

            if (stem.get()) {
                event.renderer.line(x, entity.getY(), z, x, entity.getY() + height, z, color);
            }

            count++;
        }
    }

    private boolean shouldSkipEntity(Entity entity) {
        // Skip null entities
        if (entity == null) return true;

        // Skip self if configured
        if (entity == mc.player && ignoreSelf.get()) return true;

        // Skip friends if configured
        if (ignoreFriends.get() && entity instanceof PlayerEntity && Friends.get().isFriend((PlayerEntity) entity))
            return true;

        // Skip invisible entities if configured
        if (!showInvisible.get() && entity.isInvisible())
            return true;

        // Skip entities not in our list
        if (!entities.get().contains(entity.getType()))
            return true;

        // Skip entities beyond distance (NO LIMIT! This is what we want to bypass)
        // if (mc.player.squaredDistanceTo(entity) > maxDistanceSq.get())
        //    return true;

        return false;
    }

    private Color getEntityColor(Entity entity) {
        // This is simplified from the original Tracers module
        Color color;

        if (entity instanceof PlayerEntity) {
            color = playersColor.get();
        } else {
            switch (entity.getType().getSpawnGroup()) {
                case CREATURE -> color = animalsColor.get();
                case MONSTER -> color = monstersColor.get();
                default -> color = new Color(255, 255, 255);
            }
        }

        return new Color(color);
    }

    @Override
    public String getInfoString() {
        return Integer.toString(count);
    }
}
