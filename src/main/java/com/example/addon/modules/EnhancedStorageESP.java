package com.example.addon.modules;

import com.example.addon.BypassAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import meteordevelopment.meteorclient.utils.world.Dir;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

public class EnhancedStorageESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgBypass = settings.createGroup("Bypass");

    // Cache of positions and block types we've detected
    private final Map<BlockPos, Block> detectedStorageBlocks = new HashMap<>();
    private final Set<BlockPos> scannedPositions = new HashSet<>();

    // General settings
    private final Setting<Double> renderDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("render-distance")
        .description("The maximum distance to render storage blocks.")
        .defaultValue(10000.0)
        .min(0.0)
        .sliderMax(20000.0)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Integer> fillOpacity = sgGeneral.add(new IntSetting.Builder()
        .name("fill-opacity")
        .description("The opacity of the shape fill.")
        .defaultValue(50)
        .range(0, 255)
        .sliderMax(255)
        .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draws tracers to storage blocks.")
        .defaultValue(false)
        .build()
    );

    // Block type settings
    private final Setting<Boolean> showChests = sgGeneral.add(new BoolSetting.Builder()
        .name("show-chests")
        .description("Show chests.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showEnderChests = sgGeneral.add(new BoolSetting.Builder()
        .name("show-ender-chests")
        .description("Show ender chests.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showShulkerBoxes = sgGeneral.add(new BoolSetting.Builder()
        .name("show-shulker-boxes")
        .description("Show shulker boxes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showBarrels = sgGeneral.add(new BoolSetting.Builder()
        .name("show-barrels")
        .description("Show barrels.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showCraftingTables = sgGeneral.add(new BoolSetting.Builder()
        .name("show-crafting-tables")
        .description("Show crafting tables.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showOtherStorageBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("show-other-storage-blocks")
        .description("Show other storage blocks (furnaces, dispensers, etc).")
        .defaultValue(true)
        .build()
    );

    // Bypass settings
    private final Setting<Boolean> bypassYLevel = sgBypass.add(new BoolSetting.Builder()
        .name("bypass-y-level")
        .description("Bypass y-level rendering restrictions.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> scanChunks = sgBypass.add(new BoolSetting.Builder()
        .name("scan-chunks")
        .description("Actively scan chunks for hidden storage blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> scanDelay = sgBypass.add(new IntSetting.Builder()
        .name("scan-delay")
        .description("Delay between scans in ticks.")
        .defaultValue(10)
        .range(1, 100)
        .sliderRange(1, 100)
        .visible(() -> scanChunks.get())
        .build()
    );

    private final Setting<Integer> maxY = sgBypass.add(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level to scan (to avoid server detection).")
        .defaultValue(320)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .visible(() -> scanChunks.get())
        .build()
    );

    private final Setting<Integer> minY = sgBypass.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level to scan.")
        .defaultValue(-64)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .visible(() -> scanChunks.get())
        .build()
    );

    private final Setting<Integer> scanRadius = sgBypass.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("Radius around the player to scan.")
        .defaultValue(4)
        .range(1, 10)
        .sliderRange(1, 10)
        .visible(() -> scanChunks.get())
        .build()
    );

    // Color settings
    private final Setting<SettingColor> chestColor = sgColors.add(new ColorSetting.Builder()
        .name("chest")
        .description("The color of chests.")
        .defaultValue(new SettingColor(255, 160, 0, 255))
        .build()
    );

    private final Setting<SettingColor> trappedChestColor = sgColors.add(new ColorSetting.Builder()
        .name("trapped-chest")
        .description("The color of trapped chests.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Setting<SettingColor> barrelColor = sgColors.add(new ColorSetting.Builder()
        .name("barrel")
        .description("The color of barrels.")
        .defaultValue(new SettingColor(255, 160, 0, 255))
        .build()
    );

    private final Setting<SettingColor> shulkerColor = sgColors.add(new ColorSetting.Builder()
        .name("shulker")
        .description("The color of shulker boxes.")
        .defaultValue(new SettingColor(255, 0, 255, 255))
        .build()
    );

    private final Setting<SettingColor> enderChestColor = sgColors.add(new ColorSetting.Builder()
        .name("ender-chest")
        .description("The color of ender chests.")
        .defaultValue(new SettingColor(120, 0, 255, 255))
        .build()
    );

    private final Setting<SettingColor> craftingTableColor = sgColors.add(new ColorSetting.Builder()
        .name("crafting-table")
        .description("The color of crafting tables.")
        .defaultValue(new SettingColor(42, 116, 196, 255))
        .build()
    );

    private final Setting<SettingColor> otherColor = sgColors.add(new ColorSetting.Builder()
        .name("other")
        .description("The color of other storage blocks.")
        .defaultValue(new SettingColor(140, 140, 140, 255))
        .build()
    );

    private int count = 0;
    private int scanTimer = 0;

    public EnhancedStorageESP() {
        super(BypassAddon.CATEGORY, "enhanced-storage-esp", "ESP for storage blocks that bypasses anti-xray measures.");
    }

    @Override
    public void onActivate() {
        detectedStorageBlocks.clear();
        scannedPositions.clear();
        scanTimer = 0;
        info("Enhanced Storage ESP activated with bypass mode - scanning for hidden blocks.");
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (!isActive() || mc.world == null || mc.player == null) return;

        // Skip if scanning is disabled
        if (!scanChunks.get()) return;

        scanTimer++;
        if (scanTimer < scanDelay.get()) return;
        scanTimer = 0;

        // Get player position
        BlockPos playerPos = mc.player.getBlockPos();

        // Scan area around player
        int radius = scanRadius.get();

        // Pick a random block in range to scan
        Random random = new Random();
        int x = playerPos.getX() + random.nextInt(radius * 2) - radius;
        int z = playerPos.getZ() + random.nextInt(radius * 2) - radius;

        // Scan a column of blocks at this position
        for (int y = minY.get(); y <= maxY.get(); y++) {
            BlockPos pos = new BlockPos(x, y, z);

            // Skip positions already scanned
            if (scannedPositions.contains(pos)) continue;
            scannedPositions.add(pos);

            // Skip if too far away
            double distSq = mc.player.squaredDistanceTo(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            );

            if (distSq > renderDistance.get() * renderDistance.get()) continue;

            try {
                // Get the block at this position
                Block block = mc.world.getBlockState(pos).getBlock();

                // Check if it's a storage block we care about
                if (isStorageBlock(block)) {
                    // Add to our cache
                    detectedStorageBlocks.put(pos, block);
                }
            } catch (Exception e) {
                // Ignore exceptions when trying to access blocks
            }
        }

        // Clean up old entries
        cleanupDetectedBlocks();
    }

    private void cleanupDetectedBlocks() {
        Iterator<Map.Entry<BlockPos, Block>> it = detectedStorageBlocks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Block> entry = it.next();
            BlockPos pos = entry.getKey();

            // Remove if too far away
            double distSq = mc.player.squaredDistanceTo(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            );

            if (distSq > renderDistance.get() * renderDistance.get() * 2) {
                it.remove();
                scannedPositions.remove(pos);
                continue;
            }

            // Try to check if the block is still the expected type
            try {
                Block currentBlock = mc.world.getBlockState(pos).getBlock();
                if (currentBlock != entry.getValue() && !isStorageBlock(currentBlock)) {
                    it.remove();
                    scannedPositions.remove(pos);
                }
            } catch (Exception e) {
                // Keep the block in the cache if we can't verify it
            }
        }
    }

    private boolean isStorageBlock(Block block) {
        if (block instanceof ChestBlock && showChests.get()) return true;
        if (block instanceof EnderChestBlock && showEnderChests.get()) return true;
        if (block instanceof ShulkerBoxBlock && showShulkerBoxes.get()) return true;
        if (block instanceof BarrelBlock && showBarrels.get()) return true;
        if (block instanceof CraftingTableBlock && showCraftingTables.get()) return true;

        if (showOtherStorageBlocks.get()) {
            return block instanceof AbstractFurnaceBlock ||
                block instanceof DispenserBlock ||
                block instanceof HopperBlock ||
                block instanceof DropperBlock;
        }

        return false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!isActive() || mc.world == null || mc.player == null) return;

        count = 0;

        // Process visible block entities first
        processVisibleBlocks(event);

        // Then render our detected/cached blocks
        renderDetectedBlocks(event);
    }

    private void processVisibleBlocks(Render3DEvent event) {
        // We'll use loaded chunks to get block entities
        int renderDistanceChunks = mc.options.getViewDistance().getValue();
        int playerChunkX = mc.player.getBlockPos().getX() >> 4;
        int playerChunkZ = mc.player.getBlockPos().getZ() >> 4;

        // Process all loaded chunks around the player
        for (int chunkX = playerChunkX - renderDistanceChunks; chunkX <= playerChunkX + renderDistanceChunks; chunkX++) {
            for (int chunkZ = playerChunkZ - renderDistanceChunks; chunkZ <= playerChunkZ + renderDistanceChunks; chunkZ++) {
                WorldChunk chunk = mc.world.getChunk(chunkX, chunkZ);

                if (chunk != null) {
                    // Process block entities in this chunk
                    try {
                        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                            if (blockEntity == null) continue;

                            BlockPos pos = blockEntity.getPos();

                            // Skip if beyond render distance
                            double distSq = mc.player.squaredDistanceTo(
                                pos.getX() + 0.5,
                                pos.getY() + 0.5,
                                pos.getZ() + 0.5
                            );

                            if (distSq > renderDistance.get() * renderDistance.get()) continue;

                            // Skip if beyond Y-level restrictions and bypass is not enabled
                            if (!bypassYLevel.get() && (pos.getY() < 50 || pos.getY() > 200)) continue;

                            // Get block type and skip if we're not showing this type
                            Block blockType = mc.world.getBlockState(pos).getBlock();
                            if (!shouldShowBlock(blockType)) continue;

                            // Add to our cache of detected blocks
                            detectedStorageBlocks.put(pos, blockType);

                            // Render this block entity
                            renderBlock(event, pos, blockType);
                            count++;
                        }
                    } catch (Exception e) {
                        // Ignore any errors processing this chunk
                    }
                }
            }
        }
    }

    private boolean shouldShowBlock(Block block) {
        if (block instanceof ChestBlock) return showChests.get();
        if (block instanceof EnderChestBlock) return showEnderChests.get();
        if (block instanceof ShulkerBoxBlock) return showShulkerBoxes.get();
        if (block instanceof BarrelBlock) return showBarrels.get();
        if (block instanceof CraftingTableBlock) return showCraftingTables.get();

        if (block instanceof AbstractFurnaceBlock ||
            block instanceof DispenserBlock ||
            block instanceof HopperBlock ||
            block instanceof DropperBlock) return showOtherStorageBlocks.get();

        return false;
    }

    private void renderDetectedBlocks(Render3DEvent event) {
        // Render all detected storage blocks
        for (Map.Entry<BlockPos, Block> entry : detectedStorageBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            Block block = entry.getValue();

            // Skip if too far away
            double distSq = mc.player.squaredDistanceTo(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            );

            if (distSq > renderDistance.get() * renderDistance.get()) continue;

            // Render the block
            renderBlock(event, pos, block);
            count++;
        }
    }

    private void renderBlock(Render3DEvent event, BlockPos pos, Block block) {
        // Get the appropriate color for this block
        Color color = getBlockColor(block);

        // Skip if we don't care about this block type or color is null
        if (color == null) return;

        // Prepare colors for rendering
        Color sideColor = new Color(color.r, color.g, color.b, fillOpacity.get());

        // Draw tracers if enabled
        if (tracers.get()) {
            event.renderer.line(
                mc.player.getX(),
                mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()),
                mc.player.getZ(),
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                color
            );
        }

        // Render the box
        renderBlockAt(event, pos, block, sideColor, color);
    }

    private Color getBlockColor(Block block) {
        if (block instanceof TrappedChestBlock) return trappedChestColor.get();
        else if (block instanceof ChestBlock) return chestColor.get();
        else if (block instanceof BarrelBlock) return barrelColor.get();
        else if (block instanceof ShulkerBoxBlock) return shulkerColor.get();
        else if (block instanceof EnderChestBlock) return enderChestColor.get();
        else if (block instanceof CraftingTableBlock) return craftingTableColor.get();
        else if (block instanceof AbstractFurnaceBlock ||
            block instanceof DispenserBlock ||
            block instanceof HopperBlock ||
            block instanceof DropperBlock) return otherColor.get();
        else return null;
    }

    private void renderBlockAt(Render3DEvent event, BlockPos pos, Block block, Color sideColor, Color lineColor) {
        double x1 = pos.getX();
        double y1 = pos.getY();
        double z1 = pos.getZ();

        double x2 = pos.getX() + 1;
        double y2 = pos.getY() + 1;
        double z2 = pos.getZ() + 1;

        int excludeDir = 0;

        // Handle special case for chest blocks to make them look nice
        if (block instanceof ChestBlock) {
            try {
                BlockState state = mc.world.getBlockState(pos);
                if ((state.getBlock() == Blocks.CHEST || state.getBlock() == Blocks.TRAPPED_CHEST) &&
                    state.contains(ChestBlock.CHEST_TYPE) &&
                    state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                    Direction facing = state.get(ChestBlock.FACING);
                    excludeDir = Dir.get(facing);
                }
            } catch (Exception e) {
                // Ignore errors when trying to get block state
            }
        }

        // Adjust dimensions for chest and ender chest to make them look better
        if (block instanceof ChestBlock || block instanceof EnderChestBlock) {
            double a = 1.0 / 16.0;

            if (Dir.isNot(excludeDir, Dir.WEST)) x1 += a;
            if (Dir.isNot(excludeDir, Dir.NORTH)) z1 += a;

            if (Dir.isNot(excludeDir, Dir.EAST)) x2 -= a;
            y2 -= a * 2;
            if (Dir.isNot(excludeDir, Dir.SOUTH)) z2 -= a;
        }

        // Render the box
        event.renderer.box(x1, y1, z1, x2, y2, z2, sideColor, lineColor, shapeMode.get(), excludeDir);
    }

    @Override
    public String getInfoString() {
        return Integer.toString(count);
    }
}
