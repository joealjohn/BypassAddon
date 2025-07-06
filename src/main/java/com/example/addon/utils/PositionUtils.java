package com.example.addon.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Utility class for advanced position calculations and ESP bypass functionality.
 */
public class PositionUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    
    /**
     * Generates positions for underground scanning in an optimized pattern.
     * @param center Center position for scanning
     * @param radius Scanning radius
     * @param density Scanning density (1 = every block, 2 = every other block, etc.)
     * @return List of positions to scan
     */
    public static List<BlockPos> generateUndergroundScanPositions(BlockPos center, int radius, int density) {
        List<BlockPos> positions = new ArrayList<>();
        
        for (int x = -radius; x <= radius; x += density) {
            for (int z = -radius; z <= radius; z += density) {
                // Focus on underground levels
                for (int y = -64; y <= 16; y += Math.max(1, density / 2)) {
                    BlockPos pos = center.add(x, y - center.getY(), z);
                    positions.add(pos);
                }
            }
        }
        
        return positions;
    }
    
    /**
     * Calculates positions that are most likely to contain valuable blocks.
     * @param playerPos Current player position
     * @param scanRadius Radius to scan around player
     * @return Set of high-priority positions to check
     */
    public static Set<BlockPos> getHighPriorityPositions(Vec3d playerPos, int scanRadius) {
        Set<BlockPos> positions = new HashSet<>();
        BlockPos playerBlock = BlockPos.ofFloored(playerPos);
        
        // Focus on typical ore generation levels
        int[] oreLevels = {-59, -54, -48, -32, -16, 0, 8, 15}; // Common ore Y levels
        
        for (int level : oreLevels) {
            for (int x = -scanRadius; x <= scanRadius; x += 3) {
                for (int z = -scanRadius; z <= scanRadius; z += 3) {
                    positions.add(new BlockPos(playerBlock.getX() + x, level, playerBlock.getZ() + z));
                }
            }
        }
        
        return positions;
    }
    
    /**
     * Determines if a position is safe to access without triggering anti-cheat.
     * @param pos Position to check
     * @param playerPos Current player position
     * @return True if position is safe to access
     */
    public static boolean isSafePosition(BlockPos pos, Vec3d playerPos) {
        double distance = Math.sqrt(playerPos.squaredDistanceTo(
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        
        // Don't scan positions too far away
        if (distance > 200) return false;
        
        // Avoid scanning at extreme heights
        if (pos.getY() > 320 || pos.getY() < -64) return false;
        
        return true;
    }
    
    /**
     * Gets positions around a block that should be checked for exposure.
     * @param pos Center position
     * @return List of adjacent positions
     */
    public static List<BlockPos> getAdjacentPositions(BlockPos pos) {
        List<BlockPos> adjacent = new ArrayList<>();
        
        // Add all 6 adjacent positions
        adjacent.add(pos.up());
        adjacent.add(pos.down());
        adjacent.add(pos.north());
        adjacent.add(pos.south());
        adjacent.add(pos.east());
        adjacent.add(pos.west());
        
        return adjacent;
    }
    
    /**
     * Calculates the best spoofed position to reveal underground areas.
     * @param targetArea Area we want to reveal
     * @param originalPos Original player position
     * @return Optimal spoofed position
     */
    public static Vec3d calculateOptimalSpoofPosition(BlockPos targetArea, Vec3d originalPos) {
        // Position should be below the target area to force server to send block data
        double spoofY = Math.min(targetArea.getY() - 10, 15);
        
        // Keep X and Z similar to original to avoid suspicion
        double spoofX = originalPos.x + (Math.random() - 0.5) * 2;
        double spoofZ = originalPos.z + (Math.random() - 0.5) * 2;
        
        return new Vec3d(spoofX, spoofY, spoofZ);
    }
    
    /**
     * Determines if a block position is likely to be hidden by anti-xray.
     * @param pos Position to check
     * @return True if likely hidden by anti-xray
     */
    public static boolean isLikelyAntiXrayHidden(BlockPos pos) {
        // Blocks below Y=16 are commonly hidden by anti-xray
        if (pos.getY() <= 16) return true;
        
        // Blocks at common ore levels
        int y = pos.getY();
        if ((y >= -64 && y <= -48) || // Deep slate diamond level
            (y >= -32 && y <= -16) || // Gold/redstone level
            (y >= 5 && y <= 12)) {    // Surface ore level
            return true;
        }
        
        return false;
    }
    
    /**
     * Gets chunks that should be prioritized for scanning.
     * @param playerChunkPos Player's current chunk position
     * @param radius Radius in chunks
     * @return List of chunk positions in priority order
     */
    public static List<ChunkPos> getPriorityChunks(ChunkPos playerChunkPos, int radius) {
        List<ChunkPos> chunks = new ArrayList<>();
        
        // Add chunks in spiral pattern starting from player
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) == r || Math.abs(dz) == r || r == 0) {
                        chunks.add(new ChunkPos(
                            playerChunkPos.x + dx,
                            playerChunkPos.z + dz
                        ));
                    }
                }
            }
        }
        
        return chunks;
    }
    
    /**
     * Calculates positions for freecam exploration.
     * @param startPos Starting position
     * @param direction Movement direction
     * @param distance Movement distance
     * @return Target position for freecam
     */
    public static Vec3d calculateFreecamTarget(Vec3d startPos, Vec3d direction, double distance) {
        Vec3d normalized = direction.normalize();
        Vec3d target = startPos.add(normalized.multiply(distance));
        
        // Clamp to reasonable bounds
        target = new Vec3d(
            Math.max(-30000000, Math.min(30000000, target.x)),
            Math.max(-64, Math.min(320, target.y)),
            Math.max(-30000000, Math.min(30000000, target.z))
        );
        
        return target;
    }
    
    /**
     * Determines if a position change should trigger packet spoofing.
     * @param oldPos Previous position
     * @param newPos New position
     * @param spoofThreshold Distance threshold for spoofing
     * @return True if spoofing should be triggered
     */
    public static boolean shouldTriggerSpoof(Vec3d oldPos, Vec3d newPos, double spoofThreshold) {
        double distance = oldPos.distanceTo(newPos);
        double yChange = Math.abs(newPos.y - oldPos.y);
        
        // Trigger spoofing for significant movement or Y changes
        return distance > spoofThreshold || yChange > 5.0;
    }
    
    /**
     * Gets the optimal block scanning pattern for a chunk.
     * @param chunkPos Chunk to scan
     * @param minY Minimum Y level
     * @param maxY Maximum Y level
     * @param step Step size for scanning
     * @return List of positions to scan in the chunk
     */
    public static List<BlockPos> getChunkScanPattern(ChunkPos chunkPos, int minY, int maxY, int step) {
        List<BlockPos> positions = new ArrayList<>();
        
        int chunkX = chunkPos.x * 16;
        int chunkZ = chunkPos.z * 16;
        
        // Use a scattered pattern to avoid detection
        for (int x = 0; x < 16; x += step) {
            for (int z = 0; z < 16; z += step) {
                for (int y = minY; y <= maxY; y += step) {
                    positions.add(new BlockPos(chunkX + x, y, chunkZ + z));
                }
            }
        }
        
        return positions;
    }
    
    /**
     * Calculates the render priority for a block position.
     * @param pos Block position
     * @param playerPos Player position
     * @param block Block type at position
     * @return Priority value (higher = more important to render)
     */
    public static double calculateRenderPriority(BlockPos pos, Vec3d playerPos, Block block) {
        double distance = Math.sqrt(playerPos.squaredDistanceTo(
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        
        // Distance factor (closer = higher priority)
        double distanceFactor = 1.0 / (1.0 + distance / 32.0);
        
        // Block value factor
        double valueFactor = getBlockValue(block);
        
        // Depth factor (deeper = potentially more valuable)
        double depthFactor = Math.max(0.5, (100 - pos.getY()) / 100.0);
        
        return distanceFactor * valueFactor * depthFactor;
    }
    
    /**
     * Gets the relative value of a block for rendering priority.
     * @param block Block to evaluate
     * @return Value factor (higher = more valuable)
     */
    private static double getBlockValue(Block block) {
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return 10.0;
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) return 8.0;
        if (block == Blocks.ANCIENT_DEBRIS) return 9.0;
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) return 6.0;
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) return 4.0;
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) return 3.0;
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) return 3.0;
        if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) return 7.0;
        if (block == Blocks.ENDER_CHEST) return 8.0;
        if (block == Blocks.SPAWNER) return 9.0;
        
        return 1.0; // Default value
    }
    
    /**
     * Determines if a position is within the effective range for ESP.
     * @param pos Position to check
     * @param playerPos Player position
     * @param maxRange Maximum effective range
     * @return True if within effective range
     */
    public static boolean isWithinEffectiveRange(BlockPos pos, Vec3d playerPos, double maxRange) {
        double distance = Math.sqrt(playerPos.squaredDistanceTo(
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        
        return distance <= maxRange;
    }
}