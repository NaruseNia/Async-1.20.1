package com.axalotl.async.serdes.filter;

import com.axalotl.async.config.BlockEntityLists;
import com.axalotl.async.serdes.ISerDesHookType;
import com.axalotl.async.serdes.SerDesRegistry;
import com.axalotl.async.serdes.pools.ChunkLockPool;
import com.axalotl.async.serdes.pools.ISerDesPool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Legacy implementation of the serialization/deserialization filter.
 * Uses predefined blacklists and whitelists from BlockEntityLists.
 */
public final class LegacyFilter implements ISerDesFilter {

    private static final String POOL_NAME = "LEGACY";
    private static final String RANGE_KEY = "range";
    private static final String DEFAULT_RANGE = "1";
    
    private volatile ISerDesPool primaryPool;
    private volatile ISerDesPool.ISerDesOptions poolConfig;

    @Override
    public void init() {
        this.primaryPool = SerDesRegistry.getOrCreatePool(POOL_NAME, ChunkLockPool::new);
        this.poolConfig = createPoolConfig();
    }

    /**
     * Creates the configuration for the serialization pool
     *
     * @return compiled pool configuration
     */
    private ISerDesPool.ISerDesOptions createPoolConfig() {
        Map<String, Object> config = Collections.singletonMap(RANGE_KEY, DEFAULT_RANGE);
        return primaryPool.compileOptions(config);
    }

    @Override
    public void serialise(@NotNull Runnable task, 
                         @NotNull Object obj, 
                         @NotNull BlockPos blockPos, 
                         @NotNull World world,
                         @Nullable ISerDesHookType hookType) {
        validateState();
        
        primaryPool.serialise(task, obj, blockPos, world, poolConfig);
    }

    /**
     * Validates that the filter has been properly initialized
     *
     * @throws IllegalStateException if the filter hasn't been initialized
     */
    private void validateState() {
        if (primaryPool == null || poolConfig == null) {
            throw new IllegalStateException("LegacyFilter has not been initialized");
        }
    }

    @Override
    @Nullable
    public Set<Class<?>> getTargets() {
        return BlockEntityLists.teBlackList;
    }

    @Override
    @Nullable
    public Set<Class<?>> getWhitelist() {
        return BlockEntityLists.teWhiteList;
    }

    @Override
    @NotNull
    public ClassMode getModeOnline(@NotNull Class<?> clazz) {
        if (BlockEntityLists.teBlackList != null && 
            BlockEntityLists.teBlackList.contains(clazz)) {
            return ClassMode.BLACKLIST;
        }
        if (BlockEntityLists.teWhiteList != null && 
            BlockEntityLists.teWhiteList.contains(clazz)) {
            return ClassMode.WHITELIST;
        }
        return ClassMode.UNKNOWN;
    }

    /**
     * Gets the current serialization pool
     *
     * @return the current pool, or null if not initialized
     */
    @Nullable
    public ISerDesPool getPool() {
        return primaryPool;
    }

    /**
     * Gets the current pool configuration
     *
     * @return the current configuration, or null if not initialized
     */
    @Nullable
    public ISerDesPool.ISerDesOptions getPoolConfig() {
        return poolConfig;
    }
}