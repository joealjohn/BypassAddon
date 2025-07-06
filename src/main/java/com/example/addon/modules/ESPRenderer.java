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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ESPRenderer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBlocks = settings.createGroup("Block Types");
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgFilters = settings.createGroup("Filters");

    // General settings
    private final Setting<Double> renderDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("render-distance")
        .description("Maximum distance to render blocks.")
        .defaultValue(64.0)
        .min(8.0)
        .max(512.0)
        .sliderRange(8.0, 128.0)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How to render the blocks.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Integer> fillOpacity = sgGeneral.add(new IntSetting.Builder()
        .name("fill-opacity")
        .description("Opacity of the fill.")
        .defaultValue(50)
        .range(0, 255)
        .sliderMax(255)
        .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> fadeWithDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("fade-with-distance")
        .description("Fade blocks based on distance.")
        .defaultValue(true)
        .build()
    );

    // Block type settings
    private final Setting<Boolean> showOres = sgBlocks.add(new BoolSetting.Builder()
        .name("show-ores")
        .description("Render valuable ores.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showStorage = sgBlocks.add(new BoolSetting.Builder()
        .name("show-storage")
        .description("Render storage blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showUtility = sgBlocks.add(new BoolSetting.Builder()
        .name("show-utility")
        .description("Render utility blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showSpawners = sgBlocks.add(new BoolSetting.Builder()
        .name("show-spawners")
        .description("Render mob spawners.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showLiquids = sgBlocks.add(new BoolSetting.Builder()
        .name("show-liquids")
        .description("Render lava and water sources.")
        .defaultValue(false)
        .build()
    );

    // Filters
    private final Setting<Integer> minY = sgFilters.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level to render.")
        .defaultValue(-64)
        .range(-64, 320)
        .sliderRange(-64, 100)
        .build()
    );

    private final Setting<Integer> maxY = sgFilters.add(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level to render.")
        .defaultValue(16)
        .range(-64, 320)
        .sliderRange(-10, 100)
        .build()
    );

    private final Setting<Boolean> onlyUnderground = sgFilters.add(new BoolSetting.Builder()
        .name("only-underground")
        .description("Only render blocks that are underground/hidden.")
        .defaultValue(true)
        .build()
    );

    // Colors
    private final Setting<SettingColor> diamondColor = sgColors.add(new ColorSetting.Builder()
        .name("diamond")
        .description("Color for diamond ore.")
        .defaultValue(new SettingColor(0, 255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> emeraldColor = sgColors.add(new ColorSetting.Builder()
        .name("emerald")
        .description("Color for emerald ore.")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .build()
    );

    private final Setting<SettingColor> ancientDebrisColor = sgColors.add(new ColorSetting.Builder()
        .name("ancient-debris")
        .description("Color for ancient debris.")
        .defaultValue(new SettingColor(128, 64, 0, 255))
        .build()
    );

    private final Setting<SettingColor> goldColor = sgColors.add(new ColorSetting.Builder()
        .name("gold")
        .description("Color for gold ore.")
        .defaultValue(new SettingColor(255, 215, 0, 255))
        .build()
    );

    private final Setting<SettingColor> ironColor = sgColors.add(new ColorSetting.Builder()
        .name("iron")
        .description("Color for iron ore.")
        .defaultValue(new SettingColor(192, 192, 192, 255))
        .build()
    );

    private final Setting<SettingColor> redstoneColor = sgColors.add(new ColorSetting.Builder()
        .name("redstone")
        .description("Color for redstone ore.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Setting<SettingColor> lapisColor = sgColors.add(new ColorSetting.Builder()
        .name("lapis")
        .description("Color for lapis ore.")
        .defaultValue(new SettingColor(0, 0, 255, 255))
        .build()
    );

    private final Setting<SettingColor> chestColor = sgColors.add(new ColorSetting.Builder()
        .name("chest")
        .description("Color for chests.")
        .defaultValue(new SettingColor(255, 160, 0, 255))
        .build()
    );

    private final Setting<SettingColor> spawnerColor = sgColors.add(new ColorSetting.Builder()
        .name("spawner")
        .description("Color for mob spawners.")
        .defaultValue(new SettingColor(255, 0, 255, 255))
        .build()
    );

    private final Setting<SettingColor> lavaColor = sgColors.add(new ColorSetting.Builder()
        .name("lava")
        .description("Color for lava.")
        .defaultValue(new SettingColor(255, 100, 0, 255))
        .build()
    );

    // Block cache and management
    private final Map<BlockPos, BlockData> cachedBlocks = new ConcurrentHashMap<>();
    private final Set<BlockPos> processedPositions = ConcurrentHashMap.newKeySet();
    private int renderCount = 0;

    private static class BlockData {
        public final Block block;
        public final long timestamp;
        public final boolean isExposed;

        public BlockData(Block block, boolean isExposed) {
            this.block = block;
            this.timestamp = System.currentTimeMillis();
            this.isExposed = isExposed;
        }
    }

    public ESPRenderer() {
        super(BypassAddon.CATEGORY, "esp-renderer", "Advanced ESP renderer for underground blocks with anti-xray bypass.");
    }

    @Override
    public void onActivate() {
        cachedBlocks.clear();
        processedPositions.clear();
        info("ESP Renderer activated - showing underground blocks");
    }

    @Override
    public void onDeactivate() {
        cachedBlocks.clear();
        processedPositions.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        // Clean up old entries periodically
        if (mc.player.age % 100 == 0) {
            cleanupCache();
        }
    }

    private void cleanupCache() {
        long currentTime = System.currentTimeMillis();
        double maxDistSq = Math.pow(renderDistance.get() * 2, 2);

        cachedBlocks.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            BlockData data = entry.getValue();

            // Remove if too old (5 minutes)
            if (currentTime - data.timestamp > 300000) {
                processedPositions.remove(pos);
                return true;
            }

            // Remove if too far away
            double distSq = mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distSq > maxDistSq) {
                processedPositions.remove(pos);
                return true;
            }

            return false;
        });
    }

    public void addBlock(BlockPos pos, Block block) {
        if (!shouldShowBlock(block)) return;
        
        boolean isExposed = isBlockExposed(pos);
        if (onlyUnderground.get() && isExposed) return;

        cachedBlocks.put(pos, new BlockData(block, isExposed));
    }

    public void addBlocksFromChunk(WorldChunk chunk) {
        if (chunk == null) return;

        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;

        // Sample blocks from the chunk
        for (int x = 0; x < 16; x += 2) { // Sample every 2nd block to reduce load
            for (int z = 0; z < 16; z += 2) {
                for (int y = minY.get(); y <= maxY.get(); y += 1) {
                    BlockPos pos = new BlockPos(chunkX * 16 + x, y, chunkZ * 16 + z);
                    
                    if (processedPositions.contains(pos)) continue;
                    processedPositions.add(pos);

                    try {
                        Block block = mc.world.getBlockState(pos).getBlock();
                        addBlock(pos, block);
                    } catch (Exception e) {
                        // Ignore errors accessing blocks
                    }
                }
            }
        }
    }

    private boolean isBlockExposed(BlockPos pos) {
        // Check if any adjacent block is air or transparent
        for (Direction dir : Direction.values()) {
            BlockPos adjacent = pos.offset(dir);
            try {
                Block adjacentBlock = mc.world.getBlockState(adjacent).getBlock();
                if (adjacentBlock == Blocks.AIR || adjacentBlock == Blocks.CAVE_AIR || 
                    adjacentBlock == Blocks.VOID_AIR || adjacentBlock instanceof FluidBlock) {
                    return true;
                }
            } catch (Exception e) {
                // Assume exposed if we can't check
                return true;
            }
        }
        return false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        renderCount = 0;
        double maxDistSq = Math.pow(renderDistance.get(), 2);

        for (Map.Entry<BlockPos, BlockData> entry : cachedBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockData data = entry.getValue();

            // Distance check
            double distSq = mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distSq > maxDistSq) continue;

            // Y level filter
            if (pos.getY() < minY.get() || pos.getY() > maxY.get()) continue;

            Color color = getBlockColor(data.block);
            if (color == null) continue;

            renderBlock(event, pos, data.block, color, distSq, maxDistSq);
            renderCount++;
        }
    }

    private void renderBlock(Render3DEvent event, BlockPos pos, Block block, Color color, double distSq, double maxDistSq) {
        int opacity = fillOpacity.get();
        
        // Apply distance fading
        if (fadeWithDistance.get()) {
            double distanceFactor = 1.0 - (distSq / maxDistSq);
            opacity = (int) (opacity * distanceFactor);
        }

        Color sideColor = new Color(color.r, color.g, color.b, opacity);
        Color lineColor = new Color(color.r, color.g, color.b, Math.min(255, opacity * 2));

        // Render tracers
        if (tracers.get()) {
            event.renderer.line(
                mc.player.getX(),
                mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()),
                mc.player.getZ(),
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                lineColor
            );
        }

        // Render block
        event.renderer.box(
            pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
            sideColor, lineColor, shapeMode.get(), 0
        );
    }

    private boolean shouldShowBlock(Block block) {
        if (isOre(block) && showOres.get()) return true;
        if (isStorage(block) && showStorage.get()) return true;
        if (isUtility(block) && showUtility.get()) return true;
        if (block instanceof SpawnerBlock && showSpawners.get()) return true;
        if (isLiquid(block) && showLiquids.get()) return true;
        return false;
    }

    private boolean isOre(Block block) {
        return block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE ||
               block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE ||
               block == Blocks.ANCIENT_DEBRIS ||
               block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE ||
               block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE ||
               block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE ||
               block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE ||
               block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE ||
               block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE;
    }

    private boolean isStorage(Block block) {
        return block instanceof ChestBlock ||
               block instanceof EnderChestBlock ||
               block instanceof ShulkerBoxBlock ||
               block instanceof BarrelBlock;
    }

    private boolean isUtility(Block block) {
        return block instanceof CraftingTableBlock ||
               block instanceof AbstractFurnaceBlock ||
               block instanceof EnchantingTableBlock ||
               block instanceof AnvilBlock;
    }

    private boolean isLiquid(Block block) {
        return block == Blocks.LAVA || block == Blocks.WATER;
    }

    private Color getBlockColor(Block block) {
        // Diamond ores
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
            return diamondColor.get();
        }
        // Emerald ores
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) {
            return emeraldColor.get();
        }
        // Ancient debris
        if (block == Blocks.ANCIENT_DEBRIS) {
            return ancientDebrisColor.get();
        }
        // Gold ores
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE) {
            return goldColor.get();
        }
        // Iron ores
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
            return ironColor.get();
        }
        // Redstone ores
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) {
            return redstoneColor.get();
        }
        // Lapis ores
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) {
            return lapisColor.get();
        }
        // Storage blocks
        if (isStorage(block)) {
            return chestColor.get();
        }
        // Spawners
        if (block instanceof SpawnerBlock) {
            return spawnerColor.get();
        }
        // Lava
        if (block == Blocks.LAVA) {
            return lavaColor.get();
        }

        return null;
    }

    public int getBlockCount() {
        return cachedBlocks.size();
    }

    @Override
    public String getInfoString() {
        return String.format("%d blocks", renderCount);
    }
}