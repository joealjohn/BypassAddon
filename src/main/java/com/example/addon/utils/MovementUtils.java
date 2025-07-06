package com.example.addon.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;

/**
 * Utility class for handling player movement and position calculations
 * specifically designed for ESP bypass functionality.
 */
public class MovementUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    
    /**
     * Calculates the optimal Y position for position spoofing based on world structure.
     * @param baseY The base Y level to spoof to
     * @param playerPos Current player position
     * @return Optimized Y position for maximum underground block revelation
     */
    public static double calculateOptimalSpoofY(double baseY, Vec3d playerPos) {
        if (mc.world == null) return baseY;
        
        // Ensure we're below typical cave generation height
        double targetY = Math.min(baseY, 15.0);
        
        // Avoid going below bedrock level
        double worldBottom = mc.world.getBottomY();
        targetY = Math.max(targetY, worldBottom + 5);
        
        // Add small variation based on player position to avoid patterns
        long seed = (long) (playerPos.x * 1000 + playerPos.z * 1000 + playerPos.y);
        double variation = (seed % 41) / 100.0 - 0.2; // ±0.2 variation
        
        return targetY + variation;
    }
    
    /**
     * Determines if a position change is safe for spoofing without detection.
     * @param from Starting position
     * @param to Target position
     * @return True if the movement is safe to spoof
     */
    public static boolean isSafeMovement(Vec3d from, Vec3d to) {
        double distance = from.distanceTo(to);
        double yChange = Math.abs(to.y - from.y);
        
        // Avoid large vertical movements that might trigger anti-cheat
        if (yChange > 50.0) return false;
        
        // Avoid teleport-like movements
        if (distance > 100.0) return false;
        
        return true;
    }
    
    /**
     * Calculates a smooth interpolated position between two points.
     * @param from Starting position
     * @param to Target position
     * @param progress Interpolation progress (0.0 to 1.0)
     * @return Interpolated position
     */
    public static Vec3d interpolatePosition(Vec3d from, Vec3d to, double progress) {
        progress = Math.max(0.0, Math.min(1.0, progress));
        return new Vec3d(
            from.x + (to.x - from.x) * progress,
            from.y + (to.y - from.y) * progress,
            from.z + (to.z - from.z) * progress
        );
    }
    
    /**
     * Gets the underground exploration range based on player position.
     * @param playerPos Current player position
     * @param baseRadius Base exploration radius
     * @return Optimal radius for underground scanning
     */
    public static int getUndergroundScanRadius(Vec3d playerPos, int baseRadius) {
        // Increase radius when player is higher up (more underground to scan)
        double heightFactor = Math.max(1.0, playerPos.y / 64.0);
        return (int) Math.min(baseRadius * heightFactor, baseRadius * 2);
    }
    
    /**
     * Calculates the priority for scanning a position based on distance and depth.
     * @param scanPos Position to scan
     * @param playerPos Player position
     * @return Priority value (higher = more priority)
     */
    public static double calculateScanPriority(BlockPos scanPos, Vec3d playerPos) {
        double distance = Math.sqrt(playerPos.squaredDistanceTo(
            scanPos.getX() + 0.5, scanPos.getY() + 0.5, scanPos.getZ() + 0.5));
        
        // Prioritize closer positions
        double distancePriority = 1.0 / (1.0 + distance / 16.0);
        
        // Prioritize deeper positions (more likely to have valuables)
        double depthPriority = Math.max(0.1, (64 - scanPos.getY()) / 64.0);
        
        return distancePriority * depthPriority;
    }
    
    /**
     * Determines if a position is likely to be underground and hidden.
     * @param pos Position to check
     * @return True if position is likely underground
     */
    public static boolean isLikelyUnderground(BlockPos pos) {
        // Consider anything below Y=32 as potentially underground
        if (pos.getY() < 32) return true;
        
        // For positions between 32-64, they might be in caves
        if (pos.getY() < 64) {
            // Could add more sophisticated cave detection here
            return true;
        }
        
        return false;
    }
    
    /**
     * Gets the maximum safe speed for freecam movement to avoid detection.
     * @param currentSpeed Current movement speed
     * @param distance Distance from original position
     * @return Safe movement speed
     */
    public static double getSafeFreecamSpeed(double currentSpeed, double distance) {
        // Reduce speed when far from original position to avoid suspicion
        if (distance > 50) {
            return Math.min(currentSpeed, 0.5);
        } else if (distance > 20) {
            return Math.min(currentSpeed, 1.0);
        }
        return currentSpeed;
    }
    
    /**
     * Applies anti-detection jitter to movement.
     * @param baseMovement Base movement vector
     * @param jitterStrength Strength of jitter (0.0 to 1.0)
     * @return Movement with applied jitter
     */
    public static Vec3d applyMovementJitter(Vec3d baseMovement, double jitterStrength) {
        if (jitterStrength <= 0) return baseMovement;
        
        double jitter = jitterStrength * 0.1; // Max 10% jitter
        double jitterX = (Math.random() - 0.5) * jitter;
        double jitterY = (Math.random() - 0.5) * jitter * 0.5; // Less Y jitter
        double jitterZ = (Math.random() - 0.5) * jitter;
        
        return baseMovement.add(jitterX, jitterY, jitterZ);
    }
    
    /**
     * Calculates the optimal chunk scanning order based on player position.
     * @param playerChunkX Player's chunk X coordinate
     * @param playerChunkZ Player's chunk Z coordinate
     * @param radius Scanning radius in chunks
     * @return Array of chunk coordinates in optimal scanning order
     */
    public static int[][] getOptimalChunkScanOrder(int playerChunkX, int playerChunkZ, int radius) {
        java.util.List<int[]> chunks = new java.util.ArrayList<>();
        
        // Generate spiral pattern for optimal scanning
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) == r || Math.abs(dz) == r) {
                        chunks.add(new int[]{playerChunkX + dx, playerChunkZ + dz});
                    }
                }
            }
        }
        
        return chunks.toArray(new int[0][]);
    }
    
    /**
     * Determines if a Y level is safe for position spoofing.
     * @param y Y coordinate to check
     * @param worldBottomY World's bottom Y coordinate
     * @return True if Y level is safe for spoofing
     */
    public static boolean isSafeSpoofY(double y, double worldBottomY) {
        // Must be above bedrock
        if (y <= worldBottomY + 1) return false;
        
        // Should be below typical surface level for maximum effectiveness
        if (y > 30) return false;
        
        return true;
    }
    
    /**
     * Calculates the render distance for blocks based on current performance.
     * @param baseDistance Base render distance
     * @param blockCount Current number of rendered blocks
     * @return Optimized render distance
     */
    public static double getOptimalRenderDistance(double baseDistance, int blockCount) {
        // Reduce render distance if too many blocks are being rendered
        if (blockCount > 10000) {
            return baseDistance * 0.5;
        } else if (blockCount > 5000) {
            return baseDistance * 0.75;
        } else if (blockCount > 1000) {
            return baseDistance * 0.9;
        }
        
        return baseDistance;
    }
}