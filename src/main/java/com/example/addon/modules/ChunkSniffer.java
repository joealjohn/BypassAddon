package com.example.addon.modules;

import com.example.addon.BypassAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkSniffer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgProcessing = settings.createGroup("Processing");
    private final SettingGroup sgFilters = settings.createGroup("Filters");

    private final Setting<Boolean> interceptChunkData = sgGeneral.add(new BoolSetting.Builder()
        .name("intercept-chunk-data")
        .description("Intercept and process chunk data packets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> interceptBlockUpdates = sgGeneral.add(new BoolSetting.Builder()
        .name("intercept-block-updates")
        .description("Intercept individual block update packets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> scanLoadedChunks = sgGeneral.add(new BoolSetting.Builder()
        .name("scan-loaded-chunks")
        .description("Continuously scan already loaded chunks for valuable blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> processingDelay = sgProcessing.add(new IntSetting.Builder()
        .name("processing-delay")
        .description("Delay between chunk processing batches (in ticks).")
        .defaultValue(2)
        .min(1)
        .max(20)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgProcessing.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("Maximum blocks to process per tick.")
        .defaultValue(1000)
        .min(100)
        .max(5000)
        .sliderRange(100, 2000)
        .build()
    );

    private final Setting<Boolean> prioritizeNearChunks = sgProcessing.add(new BoolSetting.Builder()
        .name("prioritize-near-chunks")
        .description("Process chunks closer to player first.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxChunkAge = sgProcessing.add(new IntSetting.Builder()
        .name("max-chunk-age")
        .description("Maximum age of cached chunk data in seconds.")
        .defaultValue(300)
        .min(60)
        .max(1800)
        .sliderRange(60, 600)
        .build()
    );

    private final Setting<Integer> minY = sgFilters.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level to process.")
        .defaultValue(-64)
        .range(-64, 320)
        .sliderRange(-64, 50)
        .build()
    );

    private final Setting<Integer> maxY = sgFilters.add(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level to process.")
        .defaultValue(16)
        .range(-64, 320)
        .sliderRange(-20, 100)
        .build()
    );

    private final Setting<Boolean> onlyProcessUnderground = sgFilters.add(new BoolSetting.Builder()
        .name("only-underground")
        .description("Only process blocks below Y=16 to focus on hidden content.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> chunkRadius = sgFilters.add(new IntSetting.Builder()
        .name("chunk-radius")
        .description("Radius of chunks to process around player.")
        .defaultValue(8)
        .min(1)
        .max(32)
        .sliderRange(1, 16)
        .build()
    );

    // Chunk data management
    private final Map<ChunkPos, ChunkData> processedChunks = new ConcurrentHashMap<>();
    private final Queue<ChunkPos> processingQueue = new LinkedList<>();
    private final Set<ChunkPos> queuedChunks = ConcurrentHashMap.newKeySet();
    
    // Statistics
    private int processedBlocks = 0;
    private int foundBlocks = 0;
    private int tickCounter = 0;

    private static class ChunkData {
        public final long timestamp;
        public final Set<BlockPos> valuableBlocks;
        public final boolean fullyProcessed;

        public ChunkData(Set<BlockPos> valuableBlocks, boolean fullyProcessed) {
            this.timestamp = System.currentTimeMillis();
            this.valuableBlocks = new HashSet<>(valuableBlocks);
            this.fullyProcessed = fullyProcessed;
        }
    }

    public ChunkSniffer() {
        super(BypassAddon.CATEGORY, "chunk-sniffer", "Processes chunk data to find and cache valuable underground blocks.");
    }

    @Override
    public void onActivate() {
        processedChunks.clear();
        processingQueue.clear();
        queuedChunks.clear();
        processedBlocks = 0;
        foundBlocks = 0;
        tickCounter = 0;
        
        info("Chunk Sniffer activated - processing chunk data for valuable blocks");
        
        // Queue all currently loaded chunks for processing
        if (scanLoadedChunks.get()) {
            queueLoadedChunks();
        }
    }

    @Override
    public void onDeactivate() {
        processedChunks.clear();
        processingQueue.clear();
        queuedChunks.clear();
        info("Chunk Sniffer deactivated");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        tickCounter++;

        // Process chunks periodically
        if (tickCounter % processingDelay.get() == 0) {
            processChunkQueue();
        }

        // Clean up old data
        if (tickCounter % 600 == 0) { // Every 30 seconds
            cleanupOldChunks();
        }

        // Queue nearby chunks if scanning is enabled
        if (scanLoadedChunks.get() && tickCounter % 100 == 0) { // Every 5 seconds
            queueNearbyChunks();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null) return;

        // Handle chunk data packets
        if (interceptChunkData.get() && event.packet instanceof ChunkDataS2CPacket) {
            ChunkDataS2CPacket packet = (ChunkDataS2CPacket) event.packet;
            ChunkPos chunkPos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());
            queueChunkForProcessing(chunkPos);
        }
        
        // Handle block update packets
        else if (interceptBlockUpdates.get() && event.packet instanceof BlockUpdateS2CPacket) {
            BlockUpdateS2CPacket packet = (BlockUpdateS2CPacket) event.packet;
            processBlockUpdate(packet.getPos(), packet.getState().getBlock());
        }
        
        // Handle multi-block updates
        else if (interceptBlockUpdates.get() && event.packet instanceof ChunkDeltaUpdateS2CPacket) {
            ChunkDeltaUpdateS2CPacket packet = (ChunkDeltaUpdateS2CPacket) event.packet;
            packet.visitUpdates((pos, state) -> processBlockUpdate(pos, state.getBlock()));
        }
    }

    private void queueLoadedChunks() {
        if (mc.world == null || mc.player == null) return;

        int playerChunkX = mc.player.getChunkPos().x;
        int playerChunkZ = mc.player.getChunkPos().z;
        int radius = chunkRadius.get();

        for (int x = playerChunkX - radius; x <= playerChunkX + radius; x++) {
            for (int z = playerChunkZ - radius; z <= playerChunkZ + radius; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                if (mc.world.getChunk(x, z) != null) {
                    queueChunkForProcessing(pos);
                }
            }
        }
    }

    private void queueNearbyChunks() {
        if (mc.world == null || mc.player == null) return;

        int playerChunkX = mc.player.getChunkPos().x;
        int playerChunkZ = mc.player.getChunkPos().z;
        int radius = Math.min(chunkRadius.get(), mc.options.getViewDistance().getValue());

        for (int x = playerChunkX - radius; x <= playerChunkX + radius; x++) {
            for (int z = playerChunkZ - radius; z <= playerChunkZ + radius; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                
                // Skip if already processed recently
                ChunkData existing = processedChunks.get(pos);
                if (existing != null && 
                    (System.currentTimeMillis() - existing.timestamp) < 60000) { // 1 minute
                    continue;
                }
                
                if (mc.world.getChunk(x, z) != null) {
                    queueChunkForProcessing(pos);
                }
            }
        }
    }

    private void queueChunkForProcessing(ChunkPos chunkPos) {
        if (!queuedChunks.contains(chunkPos)) {
            if (prioritizeNearChunks.get()) {
                // Add to front if near player
                double dist = getDistanceToPlayer(chunkPos);
                if (dist < 5 * 16) { // Within 5 chunks
                    ((LinkedList<ChunkPos>) processingQueue).addFirst(chunkPos);
                } else {
                    processingQueue.offer(chunkPos);
                }
            } else {
                processingQueue.offer(chunkPos);
            }
            queuedChunks.add(chunkPos);
        }
    }

    private double getDistanceToPlayer(ChunkPos chunkPos) {
        if (mc.player == null) return Double.MAX_VALUE;
        
        double chunkCenterX = chunkPos.x * 16 + 8;
        double chunkCenterZ = chunkPos.z * 16 + 8;
        
        return Math.sqrt(
            Math.pow(mc.player.getX() - chunkCenterX, 2) + 
            Math.pow(mc.player.getZ() - chunkCenterZ, 2)
        );
    }

    private void processChunkQueue() {
        int processedThisTick = 0;
        int maxBlocks = blocksPerTick.get();
        
        while (!processingQueue.isEmpty() && processedThisTick < maxBlocks) {
            ChunkPos chunkPos = processingQueue.poll();
            queuedChunks.remove(chunkPos);
            
            WorldChunk chunk = mc.world.getChunk(chunkPos.x, chunkPos.z);
            if (chunk != null) {
                int processed = processChunk(chunk, maxBlocks - processedThisTick);
                processedThisTick += processed;
            }
        }
    }

    private int processChunk(WorldChunk chunk, int maxBlocks) {
        Set<BlockPos> valuableBlocks = new HashSet<>();
        int processedCount = 0;
        boolean fullyProcessed = true;

        int minYLevel = onlyProcessUnderground.get() ? Math.min(minY.get(), 16) : minY.get();
        int maxYLevel = onlyProcessUnderground.get() ? Math.min(maxY.get(), 16) : maxY.get();

        // Sample the chunk for valuable blocks
        for (int x = 0; x < 16 && processedCount < maxBlocks; x++) {
            for (int z = 0; z < 16 && processedCount < maxBlocks; z++) {
                for (int y = minYLevel; y <= maxYLevel && processedCount < maxBlocks; y++) {
                    processedCount++;
                    
                    BlockPos pos = new BlockPos(
                        chunk.getPos().x * 16 + x,
                        y,
                        chunk.getPos().z * 16 + z
                    );
                    
                    try {
                        Block block = mc.world.getBlockState(pos).getBlock();
                        if (isValuableBlock(block)) {
                            valuableBlocks.add(pos);
                            foundBlocks++;
                            
                            // Send to ESP renderer
                            ESPRenderer espRenderer = Modules.get().get(ESPRenderer.class);
                            if (espRenderer != null && espRenderer.isActive()) {
                                espRenderer.addBlock(pos, block);
                            }
                        }
                    } catch (Exception e) {
                        // Ignore errors accessing blocks
                    }
                }
            }
        }

        if (processedCount >= maxBlocks) {
            fullyProcessed = false;
            // Re-queue for later processing
            queueChunkForProcessing(chunk.getPos());
        }

        processedChunks.put(chunk.getPos(), new ChunkData(valuableBlocks, fullyProcessed));
        processedBlocks += processedCount;
        
        return processedCount;
    }

    private void processBlockUpdate(BlockPos pos, Block block) {
        // Filter by Y level
        if (pos.getY() < minY.get() || pos.getY() > maxY.get()) return;
        if (onlyProcessUnderground.get() && pos.getY() > 16) return;

        if (isValuableBlock(block)) {
            foundBlocks++;
            
            // Send to ESP renderer
            ESPRenderer espRenderer = Modules.get().get(ESPRenderer.class);
            if (espRenderer != null && espRenderer.isActive()) {
                espRenderer.addBlock(pos, block);
            }
            
            // Update chunk data
            ChunkPos chunkPos = new ChunkPos(pos);
            ChunkData existing = processedChunks.get(chunkPos);
            if (existing != null) {
                existing.valuableBlocks.add(pos);
            }
        }
    }

    private boolean isValuableBlock(Block block) {
        // Ores
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return true;
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) return true;
        if (block == Blocks.ANCIENT_DEBRIS) return true;
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE) return true;
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) return true;
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) return true;
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) return true;
        
        // Storage
        if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) return true;
        if (block == Blocks.ENDER_CHEST) return true;
        if (block == Blocks.BARREL) return true;
        if (block instanceof net.minecraft.block.ShulkerBoxBlock) return true;
        
        // Spawners
        if (block == Blocks.SPAWNER) return true;
        
        // Liquids
        if (block == Blocks.LAVA) return true;
        
        return false;
    }

    private void cleanupOldChunks() {
        long currentTime = System.currentTimeMillis();
        long maxAge = maxChunkAge.get() * 1000L;
        
        processedChunks.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().timestamp > maxAge
        );
    }

    public Set<BlockPos> getValuableBlocksInChunk(ChunkPos chunkPos) {
        ChunkData data = processedChunks.get(chunkPos);
        return data != null ? new HashSet<>(data.valuableBlocks) : new HashSet<>();
    }

    public int getProcessedChunks() {
        return processedChunks.size();
    }

    public int getQueueSize() {
        return processingQueue.size();
    }

    @Override
    public String getInfoString() {
        return String.format("Chunks: %d | Blocks: %d | Queue: %d", 
            processedChunks.size(), foundBlocks, processingQueue.size());
    }
}