package com.example.addon.modules;

import com.example.addon.BypassAddon;
import com.example.addon.utils.MovementUtils;
import com.example.addon.utils.PositionUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;

/**
 * Master controller module that coordinates all bypass components for optimal underground exploration.
 */
public class BypassController extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCoordination = settings.createGroup("Coordination");
    private final SettingGroup sgSafety = settings.createGroup("Safety");

    private final Setting<Boolean> autoCoordinate = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-coordinate")
        .description("Automatically coordinate all bypass modules for optimal results.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enablePositionSpoof = sgCoordination.add(new BoolSetting.Builder()
        .name("enable-position-spoof")
        .description("Automatically enable position spoofing when needed.")
        .defaultValue(true)
        .visible(() -> autoCoordinate.get())
        .build()
    );

    private final Setting<Boolean> enableESPRenderer = sgCoordination.add(new BoolSetting.Builder()
        .name("enable-esp-renderer")
        .description("Automatically enable ESP renderer.")
        .defaultValue(true)
        .visible(() -> autoCoordinate.get())
        .build()
    );

    private final Setting<Boolean> enableChunkSniffer = sgCoordination.add(new BoolSetting.Builder()
        .name("enable-chunk-sniffer")
        .description("Automatically enable chunk sniffer.")
        .defaultValue(true)
        .visible(() -> autoCoordinate.get())
        .build()
    );

    private final Setting<Boolean> safetyMode = sgSafety.add(new BoolSetting.Builder()
        .name("safety-mode")
        .description("Enable additional anti-detection measures.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> coordinationDelay = sgSafety.add(new IntSetting.Builder()
        .name("coordination-delay")
        .description("Delay between module activations to avoid detection.")
        .defaultValue(20)
        .min(5)
        .max(100)
        .sliderRange(5, 60)
        .visible(() -> safetyMode.get())
        .build()
    );

    private final Setting<Boolean> adaptiveSettings = sgCoordination.add(new BoolSetting.Builder()
        .name("adaptive-settings")
        .description("Automatically adjust module settings based on situation.")
        .defaultValue(true)
        .visible(() -> autoCoordinate.get())
        .build()
    );

    private final Setting<Double> undergroundThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("underground-threshold")
        .description("Y level below which to activate underground exploration.")
        .defaultValue(32.0)
        .min(0.0)
        .max(64.0)
        .sliderRange(0.0, 64.0)
        .build()
    );

    // Module references
    private PositionSpoofer positionSpoofer;
    private FreecamBypass freecamBypass;
    private ESPRenderer espRenderer;
    private ChunkSniffer chunkSniffer;

    // State tracking
    private int tickCounter = 0;
    private boolean isUnderground = false;
    private boolean modulesActivated = false;
    private Vec3d lastPlayerPos = Vec3d.ZERO;

    public BypassController() {
        super(BypassAddon.CATEGORY, "bypass-controller", "Master controller for coordinating all bypass modules.");
    }

    @Override
    public void onActivate() {
        // Get module references
        positionSpoofer = Modules.get().get(PositionSpoofer.class);
        freecamBypass = Modules.get().get(FreecamBypass.class);
        espRenderer = Modules.get().get(ESPRenderer.class);
        chunkSniffer = Modules.get().get(ChunkSniffer.class);

        tickCounter = 0;
        modulesActivated = false;
        
        if (mc.player != null) {
            lastPlayerPos = mc.player.getPos();
        }

        info("Bypass Controller activated - coordinating bypass modules");
    }

    @Override
    public void onDeactivate() {
        // Deactivate all coordinated modules
        if (autoCoordinate.get()) {
            deactivateAllModules();
        }
        
        modulesActivated = false;
        info("Bypass Controller deactivated");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;
        Vec3d currentPos = mc.player.getPos();
        
        // Check if player is underground
        boolean wasUnderground = isUnderground;
        isUnderground = currentPos.y < undergroundThreshold.get();

        // Auto-coordinate modules when entering underground areas
        if (autoCoordinate.get() && isUnderground && !wasUnderground) {
            activateUndergroundMode();
        } else if (autoCoordinate.get() && !isUnderground && wasUnderground) {
            deactivateUndergroundMode();
        }

        // Adaptive settings adjustment
        if (adaptiveSettings.get() && tickCounter % 100 == 0) {
            adjustAdaptiveSettings();
        }

        // Coordinate module settings
        if (autoCoordinate.get() && tickCounter % 20 == 0) {
            coordinateModuleSettings();
        }

        lastPlayerPos = currentPos;
    }

    private void activateUndergroundMode() {
        if (modulesActivated) return;

        info("Entering underground mode - activating bypass modules");

        // Activate modules with safety delays
        if (enablePositionSpoof.get() && positionSpoofer != null && !positionSpoofer.isActive()) {
            activateModuleWithDelay(positionSpoofer, 0);
        }

        if (enableESPRenderer.get() && espRenderer != null && !espRenderer.isActive()) {
            activateModuleWithDelay(espRenderer, safetyMode.get() ? coordinationDelay.get() : 5);
        }

        if (enableChunkSniffer.get() && chunkSniffer != null && !chunkSniffer.isActive()) {
            activateModuleWithDelay(chunkSniffer, safetyMode.get() ? coordinationDelay.get() * 2 : 10);
        }

        modulesActivated = true;
    }

    private void deactivateUndergroundMode() {
        if (!modulesActivated) return;

        info("Exiting underground mode - deactivating bypass modules");
        deactivateAllModules();
        modulesActivated = false;
    }

    private void activateModuleWithDelay(Module module, int delay) {
        if (delay <= 0) {
            module.toggle();
        } else {
            // Schedule activation (simplified - in real implementation might use a scheduler)
            new Thread(() -> {
                try {
                    Thread.sleep(delay * 50); // Convert ticks to milliseconds
                    if (this.isActive() && !module.isActive()) {
                        module.toggle();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    private void deactivateAllModules() {
        if (positionSpoofer != null && positionSpoofer.isActive()) {
            positionSpoofer.toggle();
        }
        if (freecamBypass != null && freecamBypass.isActive()) {
            freecamBypass.toggle();
        }
        if (espRenderer != null && espRenderer.isActive()) {
            espRenderer.toggle();
        }
        if (chunkSniffer != null && chunkSniffer.isActive()) {
            chunkSniffer.toggle();
        }
    }

    private void adjustAdaptiveSettings() {
        Vec3d currentPos = mc.player.getPos();
        
        // Adjust position spoofer based on depth
        if (positionSpoofer != null && positionSpoofer.isActive()) {
            double optimalY = MovementUtils.calculateOptimalSpoofY(15.0, currentPos);
            // Settings adjustment would need access to private fields or public setters
        }

        // Adjust ESP renderer based on performance
        if (espRenderer != null && espRenderer.isActive()) {
            int blockCount = espRenderer.getBlockCount();
            double optimalDistance = MovementUtils.getOptimalRenderDistance(64.0, blockCount);
            // Settings adjustment would need access to private fields or public setters
        }

        // Adjust chunk sniffer based on movement
        if (chunkSniffer != null && chunkSniffer.isActive()) {
            double movement = lastPlayerPos.distanceTo(currentPos);
            if (movement > 10.0) {
                // Player is moving fast, prioritize nearby chunks
            }
        }
    }

    private void coordinateModuleSettings() {
        if (mc.player == null) return;
        
        Vec3d playerPos = mc.player.getPos();

        // Coordinate position spoofing with ESP rendering
        if (positionSpoofer != null && positionSpoofer.isActive() && 
            espRenderer != null && espRenderer.isActive()) {
            
            // Ensure spoofed position is optimal for revealing blocks
            BlockPos targetArea = BlockPos.ofFloored(playerPos.add(0, -20, 0));
            Vec3d optimalSpoof = PositionUtils.calculateOptimalSpoofPosition(targetArea, playerPos);
            
            // Would need public methods to coordinate settings
        }

        // Coordinate chunk sniffer with ESP renderer
        if (chunkSniffer != null && chunkSniffer.isActive() && 
            espRenderer != null && espRenderer.isActive()) {
            
            // Ensure both modules are scanning the same areas
            // Would need coordination methods between modules
        }
    }

    public boolean isUndergroundModeActive() {
        return isUnderground && modulesActivated;
    }

    public void forceActivateUndergroundMode() {
        isUnderground = true;
        activateUndergroundMode();
    }

    public void forceDeactivateUndergroundMode() {
        isUnderground = false;
        deactivateUndergroundMode();
    }

    @Override
    public String getInfoString() {
        if (!autoCoordinate.get()) return "Manual";
        if (isUnderground && modulesActivated) return "Underground Active";
        if (isUnderground) return "Underground Standby";
        return "Surface";
    }
}