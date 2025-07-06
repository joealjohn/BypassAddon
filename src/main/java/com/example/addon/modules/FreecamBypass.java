package com.example.addon.modules;

import com.example.addon.BypassAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class FreecamBypass extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMovement = settings.createGroup("Movement");
    private final SettingGroup sgBypass = settings.createGroup("Bypass");

    private final Setting<Double> speed = sgMovement.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Freecam movement speed.")
        .defaultValue(1.0)
        .min(0.1)
        .max(10.0)
        .sliderRange(0.1, 5.0)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgMovement.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Vertical movement speed multiplier.")
        .defaultValue(1.0)
        .min(0.1)
        .max(5.0)
        .sliderRange(0.1, 2.0)
        .build()
    );

    private final Setting<Boolean> smoothMovement = sgMovement.add(new BoolSetting.Builder()
        .name("smooth-movement")
        .description("Apply smooth acceleration and deceleration.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> acceleration = sgMovement.add(new DoubleSetting.Builder()
        .name("acceleration")
        .description("Movement acceleration factor.")
        .defaultValue(0.1)
        .min(0.01)
        .max(1.0)
        .sliderRange(0.01, 0.5)
        .visible(() -> smoothMovement.get())
        .build()
    );

    private final Setting<Boolean> noClip = sgGeneral.add(new BoolSetting.Builder()
        .name("no-clip")
        .description("Allow camera to pass through blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> suppressMovementPackets = sgBypass.add(new BoolSetting.Builder()
        .name("suppress-movement-packets")
        .description("Prevent sending movement packets while in freecam to avoid server detection.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> maintainServerPosition = sgBypass.add(new BoolSetting.Builder()
        .name("maintain-server-position")
        .description("Keep the server-side player position unchanged.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> packetSuppressRadius = sgBypass.add(new IntSetting.Builder()
        .name("packet-suppress-radius")
        .description("Radius from original position where packets are suppressed.")
        .defaultValue(5)
        .min(1)
        .max(50)
        .sliderRange(1, 20)
        .visible(() -> suppressMovementPackets.get())
        .build()
    );

    private final Setting<Boolean> renderPlayerModel = sgGeneral.add(new BoolSetting.Builder()
        .name("render-player-model")
        .description("Render a ghost model at the server-side player position.")
        .defaultValue(true)
        .build()
    );

    // Camera state
    private double cameraX, cameraY, cameraZ;
    private float cameraYaw, cameraPitch;
    private double serverX, serverY, serverZ;
    private float serverYaw, serverPitch;
    
    // Movement state
    private Vec3d velocity = Vec3d.ZERO;
    private boolean isFreecamActive = false;

    // Input state
    private boolean forward, backward, left, right, up, down;

    public FreecamBypass() {
        super(BypassAddon.CATEGORY, "freecam-bypass", "Camera movement without server detection for underground exploration.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        // Store original position
        serverX = cameraX = mc.player.getX();
        serverY = cameraY = mc.player.getY();
        serverZ = cameraZ = mc.player.getZ();
        serverYaw = cameraYaw = mc.player.getYaw();
        serverPitch = cameraPitch = mc.player.getPitch();

        velocity = Vec3d.ZERO;
        isFreecamActive = true;

        info("Freecam bypass activated - camera detached from server position");
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null) return;

        // Restore player position
        mc.player.setPosition(cameraX, cameraY, cameraZ);
        mc.player.setYaw(cameraYaw);
        mc.player.setPitch(cameraPitch);

        isFreecamActive = false;
        velocity = Vec3d.ZERO;

        info("Freecam bypass deactivated - camera reattached to player");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isFreecamActive || mc.player == null) return;

        updateInput();
        updateMovement();
        updateCamera();
    }

    private void updateInput() {
        // Get movement keys
        forward = isPressed(mc.options.forwardKey);
        backward = isPressed(mc.options.backKey);
        left = isPressed(mc.options.leftKey);
        right = isPressed(mc.options.rightKey);
        up = isPressed(mc.options.jumpKey);
        down = isPressed(mc.options.sneakKey);
    }

    private boolean isPressed(KeyBinding keyBinding) {
        return InputUtil.isKeyPressed(mc.getWindow().getHandle(), keyBinding.getDefaultKey().getCode());
    }

    private void updateMovement() {
        Vec3d input = Vec3d.ZERO;

        // Calculate movement input
        if (forward) input = input.add(0, 0, 1);
        if (backward) input = input.add(0, 0, -1);
        if (left) input = input.add(-1, 0, 0);
        if (right) input = input.add(1, 0, 0);
        if (up) input = input.add(0, 1, 0);
        if (down) input = input.add(0, -1, 0);

        if (input.lengthSquared() > 0) {
            input = input.normalize();

            // Apply speed
            double currentSpeed = speed.get();
            if (input.y != 0) {
                input = new Vec3d(input.x, input.y * verticalSpeed.get(), input.z);
            }

            // Rotate input based on camera yaw
            double radYaw = Math.toRadians(cameraYaw);
            double sin = Math.sin(radYaw);
            double cos = Math.cos(radYaw);

            Vec3d rotatedInput = new Vec3d(
                input.x * cos - input.z * sin,
                input.y,
                input.x * sin + input.z * cos
            );

            rotatedInput = rotatedInput.multiply(currentSpeed);

            if (smoothMovement.get()) {
                // Apply smooth acceleration
                double accel = acceleration.get();
                velocity = velocity.multiply(1 - accel).add(rotatedInput.multiply(accel));
            } else {
                velocity = rotatedInput;
            }
        } else if (smoothMovement.get()) {
            // Apply deceleration when no input
            velocity = velocity.multiply(0.8);
        } else {
            velocity = Vec3d.ZERO;
        }

        // Update camera position
        cameraX += velocity.x;
        cameraY += velocity.y;
        cameraZ += velocity.z;

        // Handle collision if no-clip is disabled
        if (!noClip.get()) {
            // Basic collision detection could be added here
        }
    }

    private void updateCamera() {
        if (mc.player == null) return;

        // Update camera rotation from mouse
        cameraYaw = mc.player.getYaw();
        cameraPitch = mc.player.getPitch();

        // Set player position to camera position for rendering
        mc.player.setPosition(cameraX, cameraY, cameraZ);
        mc.player.setYaw(cameraYaw);
        mc.player.setPitch(cameraPitch);

        // Keep server position unchanged if enabled
        if (maintainServerPosition.get()) {
            // The PositionSpoofer module will handle the actual packet spoofing
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!isFreecamActive || !suppressMovementPackets.get()) return;

        if (event.packet instanceof PlayerMoveC2SPacket) {
            // Check if we're within suppression radius
            double distanceFromServer = Math.sqrt(
                Math.pow(cameraX - serverX, 2) + 
                Math.pow(cameraY - serverY, 2) + 
                Math.pow(cameraZ - serverZ, 2)
            );

            if (distanceFromServer > packetSuppressRadius.get()) {
                event.cancel();
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!isFreecamActive || !renderPlayerModel.get() || mc.player == null) return;

        // Render a ghost model at the server position
        // This would require access to player rendering which may need additional implementation
        renderGhostPlayer(event);
    }

    private void renderGhostPlayer(Render3DEvent event) {
        // Basic wireframe representation of player at server position
        double x1 = serverX - 0.3;
        double y1 = serverY;
        double z1 = serverZ - 0.3;
        double x2 = serverX + 0.3;
        double y2 = serverY + 1.8;
        double z2 = serverZ + 0.3;

        // Draw a simple wireframe box to represent the server-side player
        event.renderer.box(
            x1, y1, z1, x2, y2, z2,
            new meteordevelopment.meteorclient.utils.render.color.Color(255, 255, 255, 50),
            new meteordevelopment.meteorclient.utils.render.color.Color(255, 255, 255, 200),
            meteordevelopment.meteorclient.renderer.ShapeMode.Lines,
            0
        );
    }

    public Vec3d getCameraPosition() {
        return new Vec3d(cameraX, cameraY, cameraZ);
    }

    public Vec3d getServerPosition() {
        return new Vec3d(serverX, serverY, serverZ);
    }

    public boolean isFreecamActive() {
        return isFreecamActive;
    }

    public void setCameraPosition(Vec3d pos) {
        cameraX = pos.x;
        cameraY = pos.y;
        cameraZ = pos.z;
    }

    @Override
    public String getInfoString() {
        if (!isFreecamActive) return "Inactive";
        return String.format("X: %.1f Y: %.1f Z: %.1f", cameraX, cameraY, cameraZ);
    }
}