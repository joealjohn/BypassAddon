package com.example.addon.modules;

import com.example.addon.BypassAddon;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
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
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import meteordevelopment.meteorclient.utils.world.Dir;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

public class ServerBypassESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    // Block database
    private final Map<BlockPos, Block> storageBlocks = new HashMap<>();
    private final Map<BlockPos, Integer> lastSeenTicks = new HashMap<>();
    private final List<BlockPos> scanQueue = new ArrayList<>();

    // General settings
    private final Setting<Double> renderDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("render-distance")
        .description("The maximum distance to render storage blocks.")
        .defaultValue(512.0)
        .min(0.0)
        .sliderMax(1000.0)
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

    private final Setting<Boolean> showTracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxBlockAge = sgGeneral.add(new IntSetting.Builder()
        .name("block-timeout")
        .description("How long to remember blocks for (in ticks).")
        .defaultValue(36000) // 30 minutes
        .min(1200)
        .sliderMax(72000)
        .build()
    );

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("Radius of blocks to scan around player.")
        .defaultValue(32)
        .min(8)
        .sliderMax(64)
        .build()
    );

    private final Setting<Integer> scanDelay = sgGeneral.add(new IntSetting.Builder()
        .name("scan-delay")
        .description("Delay between area scans (in ticks).")
        .defaultValue(20)
        .range(1, 100)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to scan each tick.")
        .defaultValue(50)
        .range(1, 500)
        .sliderRange(1, 500)
        .build()
    );

    // Block types
    private final Setting<Boolean> showChests = sgGeneral.add(new BoolSetting.Builder()
        .name("chests")
        .description("Show chests.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showEnderChests = sgGeneral.add(new BoolSetting.Builder()
        .name("ender-chests")
        .description("Show ender chests.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showShulkers = sgGeneral.add(new BoolSetting.Builder()
        .name("shulkers")
        .description("Show shulker boxes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showBarrels = sgGeneral.add(new BoolSetting.Builder()
        .name("barrels")
        .description("Show barrels.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showCraftingTables = sgGeneral.add(new BoolSetting.Builder()
        .name("crafting-tables")
        .description("Show crafting tables.")
        .defaultValue(true)
        .build()
    );

    // Colors
    private final Setting<SettingColor> chestColor = sgColors.add(new ColorSetting.Builder()
        .name("chest-color")
        .description("The color of chests.")
        .defaultValue(new SettingColor(255, 160, 0, 255))
        .build()
    );

    private final Setting<SettingColor> enderChestColor = sgColors.add(new ColorSetting.Builder()
        .name("ender-chest-color")
        .description("The color of ender chests.")
        .defaultValue(new SettingColor(120, 0, 255, 255))
        .build()
    );

    private final Setting<SettingColor> shulkerBoxColor = sgColors.add(new ColorSetting.Builder()
        .name("shulker-color")
        .description("The color of shulker boxes.")
        .defaultValue(new SettingColor(255, 0, 255, 255))
        .build()
    );

    private final Setting<SettingColor> barrelColor = sgColors.add(new ColorSetting.Builder()
        .name("barrel-color")
        .description("The color of barrels.")
        .defaultValue(new SettingColor(255, 160, 0, 255))
        .build()
    );

    private final Setting<SettingColor> craftingTableColor = sgColors.add(new ColorSetting.Builder()
        .name("crafting-table-color")
        .description("The color of crafting tables.")
        .defaultValue(new SettingColor(42, 116, 196, 255))
        .build()
    );

    private int scanTimer = 0;
    private int count = 0;

    public ServerBypassESP() {
        super(BypassAddon.CATEGORY, "server-bypass-esp", "ESP that works around server anti-xray restrictions.");
    }

    @Override
    public void onActivate() {
        info("Server Bypass ESP activated - scanning for storage blocks across all Y-levels.");
        initScan();
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        storageBlocks.clear();
        lastSeenTicks.clear();
        initScan();
    }

    private void initScan() {
        // Initialize the scan queue with blocks around the player
        scanQueue.clear();
        if (mc.player == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int radius = scanRadius.get();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = new BlockPos(
                        playerPos.getX() + x,
                        playerPos.getY() + y,
                        playerPos.getZ() + z
                    );
                    scanQueue.add(pos);
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || mc.world == null || mc.player == null) return;

        int currentTick = mc.player.age;

        // Process the scan queue
        int blocksScanThisTick = 0;
        while (!scanQueue.isEmpty() && blocksScanThisTick < blocksPerTick.get()) {
            BlockPos pos = scanQueue.remove(scanQueue.size() - 1);
            blocksScanThisTick++;

            try {
                Block block = mc.world.getBlockState(pos).getBlock();
                if (isStorageBlock(block)) {
                    storageBlocks.put(pos, block);
                    lastSeenTicks.put(pos, currentTick);
                }
            } catch (Exception e) {
                // Ignore errors accessing blocks
            }
        }

        // Periodically add more blocks to scan
        scanTimer++;
        if (scanTimer >= scanDelay.get()) {
            scanTimer = 0;
            addMoreBlocksToScan();
        }

        // Check visible blocks to keep db updated
        checkVisibleBlocks(currentTick);

        // Clean up old blocks
        cleanupOldBlocks(currentTick);
    }

    private void addMoreBlocksToScan() {
        if (mc.player == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int radius = scanRadius.get();
        Random random = new Random();

        // Add a smaller set of random blocks to scan
        for (int i = 0; i < 200; i++) {
            int x = random.nextInt(radius * 2) - radius;
            int y = random.nextInt(256) - 64; // -64 to +192 (assuming world height)
            int z = random.nextInt(radius * 2) - radius;

            BlockPos pos = new BlockPos(
                playerPos.getX() + x,
                y,  // Use absolute Y coordinate for better coverage
                playerPos.getZ() + z
            );

            if (!lastSeenTicks.containsKey(pos)) {
                scanQueue.add(pos);
            }
        }
    }

    private void checkVisibleBlocks(int currentTick) {
        if (mc.world == null) return;

        // Scan each loaded chunk for block entities
        int renderDistance = mc.options.getViewDistance().getValue();
        int playerChunkX = mc.player.getChunkPos().x;
        int playerChunkZ = mc.player.getChunkPos().z;

        // Loop through loaded chunks
        for (int x = playerChunkX - renderDistance; x <= playerChunkX + renderDistance; x++) {
            for (int z = playerChunkZ - renderDistance; z <= playerChunkZ + renderDistance; z++) {
                WorldChunk chunk = mc.world.getChunk(x, z);
                if (chunk != null) {
                    // Get block entities in this chunk
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (be == null) continue;

                        BlockPos pos = be.getPos();
                        Block block = mc.world.getBlockState(pos).getBlock();

                        if (isStorageBlock(block)) {
                            storageBlocks.put(pos, block);
                            lastSeenTicks.put(pos, currentTick);
                        }
                    }
                }
            }
        }
    }

    private void cleanupOldBlocks(int currentTick) {
        Iterator<Map.Entry<BlockPos, Integer>> it = lastSeenTicks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = it.next();
            if (currentTick - entry.getValue() > maxBlockAge.get()) {
                storageBlocks.remove(entry.getKey());
                it.remove();
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isActive() || mc.world == null || mc.player == null) return;

        // Track block updates from packets
        if (event.packet instanceof BlockUpdateS2CPacket) {
            BlockUpdateS2CPacket packet = (BlockUpdateS2CPacket) event.packet;
            Block block = packet.getState().getBlock();

            if (isStorageBlock(block)) {
                storageBlocks.put(packet.getPos(), block);
                lastSeenTicks.put(packet.getPos(), mc.player.age);
            }
        }
        // When new chunk data comes in, schedule a scan
        else if (event.packet instanceof ChunkDataS2CPacket) {
            // Reduce the scan timer to schedule a scan soon
            scanTimer = Math.max(0, scanDelay.get() - 5);
        }
    }

    private boolean isStorageBlock(Block block) {
        if (block instanceof ChestBlock && showChests.get()) return true;
        if (block instanceof EnderChestBlock && showEnderChests.get()) return true;
        if (block instanceof ShulkerBoxBlock && showShulkers.get()) return true;
        if (block instanceof BarrelBlock && showBarrels.get()) return true;
        if (block instanceof CraftingTableBlock && showCraftingTables.get()) return true;
        return false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!isActive() || mc.world == null || mc.player == null) return;

        count = 0;
        double maxDist = renderDistance.get() * renderDistance.get();

        for (Map.Entry<BlockPos, Block> entry : storageBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            Block block = entry.getValue();

            // Distance check
            double dist = mc.player.squaredDistanceTo(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            );

            if (dist > maxDist) continue;

            // Get color for this block type
            Color color = getBlockColor(block);
            if (color == null) continue;

            // Render the block
            renderBlockOutline(event, pos, block, color);
            count++;
        }
    }

    private void renderBlockOutline(Render3DEvent event, BlockPos pos, Block block, Color color) {
        Color lineColor = color;
        Color sideColor = new Color(color.r, color.g, color.b, fillOpacity.get());

        if (showTracers.get()) {
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

        // Basic box dimensions
        double x1 = pos.getX();
        double y1 = pos.getY();
        double z1 = pos.getZ();
        double x2 = x1 + 1;
        double y2 = y1 + 1;
        double z2 = z1 + 1;

        int excludeDir = 0;

        // Special case for chests to make them look nice
        if (block instanceof ChestBlock) {
            try {
                BlockState state = mc.world.getBlockState(pos);
                if (state.contains(ChestBlock.CHEST_TYPE) &&
                    state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                    Direction facing = state.get(ChestBlock.FACING);
                    excludeDir = Dir.get(facing);
                }

                // Adjust chest dimensions
                double a = 1.0 / 16.0;

                if (Dir.isNot(excludeDir, Dir.WEST)) x1 += a;
                if (Dir.isNot(excludeDir, Dir.NORTH)) z1 += a;

                if (Dir.isNot(excludeDir, Dir.EAST)) x2 -= a;
                y2 -= a * 2;
                if (Dir.isNot(excludeDir, Dir.SOUTH)) z2 -= a;
            } catch (Exception e) {
                // Ignore errors and render a basic box
            }
        }

        // Render the box
        event.renderer.box(
            x1, y1, z1,
            x2, y2, z2,
            sideColor, lineColor, shapeMode.get(), excludeDir
        );
    }

    private Color getBlockColor(Block block) {
        if (block instanceof ChestBlock) return chestColor.get();
        if (block instanceof EnderChestBlock) return enderChestColor.get();
        if (block instanceof ShulkerBoxBlock) return shulkerBoxColor.get();
        if (block instanceof BarrelBlock) return barrelColor.get();
        if (block instanceof CraftingTableBlock) return craftingTableColor.get();
        return null;
    }

    @Override
    public String getInfoString() {
        return Integer.toString(count);
    }
}
