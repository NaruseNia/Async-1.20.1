package com.axalotl.async.parallelised;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe chunk locking mechanism for concurrent chunk operations.
 * Provides methods to lock chunks in a radius around a position.
 */
public final class ChunkLock {

    @Deprecated(since = "1.0", forRemoval = true)
    public static final ChunkLock INSTANCE = new ChunkLock();

    private static final int INITIAL_CACHE_CAPACITY = 256;

    private final Map<Long, Lock> chunkLockCache;

    /**
     * Creates a new ChunkLock instance with default cache capacity
     */
    public ChunkLock() {
        this(INITIAL_CACHE_CAPACITY);
    }

    /**
     * Creates a new ChunkLock instance with specified cache capacity
     *
     * @param initialCapacity initial capacity for the lock cache
     * @throws IllegalArgumentException if initialCapacity is negative
     */
    public ChunkLock(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity cannot be negative: " + initialCapacity);
        }
        this.chunkLockCache = new ConcurrentHashMap<>(initialCapacity);
    }

    /**
     * Locks all chunks in a radius around the specified block position
     *
     * @param blockPos center position
     * @param radius radius of chunks to lock
     * @return array of locked chunk positions
     * @throws IllegalArgumentException if radius is negative
     */
    public long[] lock(@NotNull BlockPos blockPos, int radius) {
        if (blockPos == null) {
            throw new IllegalArgumentException("Block position cannot be null");
        }
        return lock(new ChunkPos(blockPos).toLong(), radius);
    }

    /**
     * Locks all chunks in a radius around the specified chunk position
     *
     * @param centerChunkPos center chunk position
     * @param radius radius of chunks to lock
     * @return array of locked chunk positions
     * @throws IllegalArgumentException if radius is negative
     */
    public long[] lock(long centerChunkPos, int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("Radius cannot be negative: " + radius);
        }

        // Calculate size and create array
        int diameter = 2 * radius + 1;
        int totalChunks = diameter * diameter;
        long[] targetChunks = new long[totalChunks];

        // Fill array with chunk positions
        int index = 0;
        ChunkPos center = new ChunkPos(centerChunkPos);
        int centerX = center.x;
        int centerZ = center.z;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                targetChunks[index++] = ChunkPos.toLong(centerX + x, centerZ + z);
            }
        }

        // Sort for consistent locking order to prevent deadlocks
        Arrays.sort(targetChunks);

        // Acquire all locks
        acquireChunkLocks(targetChunks);

        return targetChunks;
    }

    /**
     * Unlocks the specified chunk positions
     *
     * @param lockedChunks array of locked chunk positions
     * @throws IllegalArgumentException if lockedChunks is null
     */
    public void unlock(@NotNull long[] lockedChunks) {
        if (lockedChunks == null) {
            throw new IllegalArgumentException("Locked chunks array cannot be null");
        }

        // Release locks in reverse order
        for (int i = lockedChunks.length - 1; i >= 0; i--) {
            Lock lock = chunkLockCache.get(lockedChunks[i]);
            if (lock != null) {
                lock.unlock();
            }
        }
    }

    private void acquireChunkLocks(long[] chunks) {
        for (long chunkPos : chunks) {
            chunkLockCache.computeIfAbsent(chunkPos, k -> new ReentrantLock()).lock();
        }
    }

    /**
     * Returns the number of currently cached locks
     *
     * @return the size of the lock cache
     */
    public int getCacheSize() {
        return chunkLockCache.size();
    }

    /**
     * Clears all unused locks from the cache
     */
    public void clearUnusedLocks() {
        chunkLockCache.entrySet().removeIf(entry -> 
            entry.getValue() instanceof ReentrantLock lock && !lock.isLocked());
    }
}