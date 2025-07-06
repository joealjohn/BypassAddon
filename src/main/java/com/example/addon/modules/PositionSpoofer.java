package com.example.addon.modules;

import com.example.addon.BypassAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PositionAndOnGroundC2SPacket;
import net.minecraft.network.packet.c2s.play.LookAndOnGroundC2SPacket;
import net.minecraft.network.packet.c2s.play.FullC2SPacket;

public class PositionSpoofer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSpoof = settings.createGroup("Spoof Settings");

    private final Setting<Boolean> spoofYPosition = sgGeneral.add(new BoolSetting.Builder()
        .name("spoof-y-position")
        .description("Spoof the player's Y position in movement packets to trick server into revealing underground blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> spoofYLevel = sgSpoof.add(new DoubleSetting.Builder()
        .name("spoof-y-level")
        .description("The Y level to spoof the player position to (should be below Y=30 for best results).")
        .defaultValue(15.0)
        .min(-64.0)
        .max(30.0)
        .sliderRange(-64.0, 30.0)
        .visible(() -> spoofYPosition.get())
        .build()
    );

    private final Setting<Boolean> spoofOnGround = sgSpoof.add(new BoolSetting.Builder()
        .name("spoof-on-ground")
        .description("Spoof the onGround flag to match the spoofed Y position.")
        .defaultValue(true)
        .visible(() -> spoofYPosition.get())
        .build()
    );

    private final Setting<Boolean> randomizeOffset = sgSpoof.add(new BoolSetting.Builder()
        .name("randomize-offset")
        .description("Add small random offsets to avoid detection patterns.")
        .defaultValue(true)
        .visible(() -> spoofYPosition.get())
        .build()
    );

    private final Setting<Double> randomizeRange = sgSpoof.add(new DoubleSetting.Builder()
        .name("randomize-range")
        .description("Range for random offset (±)")
        .defaultValue(2.0)
        .min(0.0)
        .max(5.0)
        .sliderRange(0.0, 5.0)
        .visible(() -> spoofYPosition.get() && randomizeOffset.get())
        .build()
    );

    private final Setting<Integer> spoofDelay = sgSpoof.add(new IntSetting.Builder()
        .name("spoof-delay")
        .description("Delay between spoofed packets (in ticks) to avoid detection.")
        .defaultValue(5)
        .min(1)
        .max(20)
        .sliderRange(1, 20)
        .visible(() -> spoofYPosition.get())
        .build()
    );

    private final Setting<Boolean> maintainCamera = sgGeneral.add(new BoolSetting.Builder()
        .name("maintain-camera")
        .description("Keep the player's camera above ground while spoofing position.")
        .defaultValue(true)
        .build()
    );

    private double originalY = 0;
    private double originalX = 0; 
    private double originalZ = 0;
    private int tickCounter = 0;
    private boolean isActive = false;

    public PositionSpoofer() {
        super(BypassAddon.CATEGORY, "position-spoofer", "Spoofs player position packets to trick servers into revealing underground blocks.");
    }

    @Override
    public void onActivate() {
        if (mc.player != null) {
            originalY = mc.player.getY();
            originalX = mc.player.getX();
            originalZ = mc.player.getZ();
            isActive = true;
            info("Position spoofing activated - spoofing Y position to " + spoofYLevel.get());
        }
    }

    @Override
    public void onDeactivate() {
        isActive = false;
        info("Position spoofing deactivated");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive || mc.player == null) return;

        tickCounter++;
        
        // Update original position tracking
        if (!spoofYPosition.get()) {
            originalY = mc.player.getY();
        }
        originalX = mc.player.getX();
        originalZ = mc.player.getZ();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!isActive || !spoofYPosition.get() || mc.player == null) return;

        // Only spoof every N ticks to avoid detection
        if (tickCounter % spoofDelay.get() != 0) return;

        if (event.packet instanceof PlayerMoveC2SPacket packet) {
            // Calculate spoofed Y position
            double spoofedY = calculateSpoofedY();
            boolean spoofedOnGround = spoofOnGround.get() && spoofedY <= mc.player.getWorld().getBottomY() + 5;

            // Create modified packet based on the original packet type
            PlayerMoveC2SPacket modifiedPacket = null;

            if (packet instanceof FullC2SPacket) {
                FullC2SPacket fullPacket = (FullC2SPacket) packet;
                modifiedPacket = new FullC2SPacket(
                    fullPacket.getX(0), // Keep original X
                    spoofedY,           // Spoofed Y
                    fullPacket.getZ(0), // Keep original Z
                    fullPacket.getYaw(0),
                    fullPacket.getPitch(0),
                    spoofedOnGround,
                    fullPacket.horizontalCollision()
                );
            } else if (packet instanceof PositionAndOnGroundC2SPacket) {
                PositionAndOnGroundC2SPacket posPacket = (PositionAndOnGroundC2SPacket) packet;
                modifiedPacket = new PositionAndOnGroundC2SPacket(
                    posPacket.getX(0), // Keep original X
                    spoofedY,          // Spoofed Y
                    posPacket.getZ(0), // Keep original Z
                    spoofedOnGround,
                    posPacket.horizontalCollision()
                );
            } else if (packet instanceof LookAndOnGroundC2SPacket) {
                // Don't modify look-only packets
                return;
            }

            if (modifiedPacket != null) {
                event.cancel();
                mc.getNetworkHandler().sendPacket(modifiedPacket);
            }
        }
    }

    private double calculateSpoofedY() {
        double baseY = spoofYLevel.get();
        
        if (randomizeOffset.get()) {
            double range = randomizeRange.get();
            double offset = (Math.random() - 0.5) * 2 * range;
            baseY += offset;
        }
        
        // Ensure we don't go below world bottom
        if (mc.player != null && mc.player.getWorld() != null) {
            double worldBottom = mc.player.getWorld().getBottomY();
            baseY = Math.max(baseY, worldBottom + 1);
        }
        
        return baseY;
    }

    public double getOriginalY() {
        return originalY;
    }

    public double getSpoofedY() {
        return spoofYPosition.get() ? calculateSpoofedY() : originalY;
    }

    public boolean isMaintainCamera() {
        return maintainCamera.get();
    }

    public boolean isPositionSpoofing() {
        return isActive && spoofYPosition.get();
    }

    @Override
    public String getInfoString() {
        if (!isActive || !spoofYPosition.get()) return "Off";
        return String.format("Y: %.1f", getSpoofedY());
    }
}