package com.axalotl.async.serdes.filter;

import com.axalotl.async.serdes.ISerDesHookType;
import com.axalotl.async.serdes.SerDesRegistry;
import com.axalotl.async.serdes.pools.ChunkLockPool;
import com.axalotl.async.serdes.pools.ISerDesPool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AutoFilter implements ISerDesFilter {
    
    private static volatile AutoFilter instance;
    private static final Object LOCK = new Object();
    
    private volatile ISerDesPool pool;
    private final Set<Class<?>> blacklist;
    private final Set<Class<?>> whitelist;

    private AutoFilter() {
        this.blacklist = ConcurrentHashMap.newKeySet();
        this.whitelist = ConcurrentHashMap.newKeySet();
    }

    public static AutoFilter singleton() {
        AutoFilter result = instance;
        if (result == null) {
            synchronized (LOCK) {
                result = instance;
                if (result == null) {
                    result = new AutoFilter();
                    instance = result;
                }
            }
        }
        return result;
    }

    @Override
    public void init() {
        this.pool = SerDesRegistry.getOrCreatePool("AUTO", ChunkLockPool::new);
    }

    @Override
    public void serialise(@NotNull Runnable task, 
                         @NotNull Object obj, 
                         @NotNull BlockPos blockPos, 
                         @NotNull World world, 
                         ISerDesHookType hookType) {
        Objects.requireNonNull(task, "Task cannot be null");
        Objects.requireNonNull(obj, "Object cannot be null");
        Objects.requireNonNull(blockPos, "BlockPos cannot be null");
        Objects.requireNonNull(world, "World cannot be null");
        
        if (pool == null) {
            throw new IllegalStateException("AutoFilter has not been initialized");
        }
        
        pool.serialise(task, hookType, blockPos, world, null);
    }

    @Override
    @Nullable
    public Set<Class<?>> getTargets() {
        return blacklist.isEmpty() ? null : Collections.unmodifiableSet(blacklist);
    }

    @Override
    @Nullable
    public Set<Class<?>> getWhitelist() {
        return whitelist.isEmpty() ? null : Collections.unmodifiableSet(whitelist);
    }

    @Override
    @NotNull
    public ClassMode getModeOnline(@NotNull Class<?> clazz) {
        Objects.requireNonNull(clazz, "Class cannot be null");
        if (blacklist.contains(clazz)) {
            return ClassMode.BLACKLIST;
        }
        if (whitelist.contains(clazz)) {
            return ClassMode.WHITELIST;
        }
        return ClassMode.UNKNOWN;
    }

    /**
     * Adds a class to the blacklist. Blacklisted classes will not be processed.
     *
     * @param clazz the class to blacklist
     * @throws NullPointerException if clazz is null
     */
    public void addClassToBlacklist(@NotNull Class<?> clazz) {
        Objects.requireNonNull(clazz, "Class cannot be null");
        blacklist.add(clazz);
        whitelist.remove(clazz); // Ensure class is not in both lists
    }

    /**
     * Adds a class to the whitelist.
     *
     * @param clazz the class to whitelist
     * @throws NullPointerException if clazz is null
     */
    public void addClassToWhitelist(@NotNull Class<?> clazz) {
        Objects.requireNonNull(clazz, "Class cannot be null");
        whitelist.add(clazz);
        blacklist.remove(clazz); // Ensure class is not in both lists
    }

    /**
     * Removes a class from the blacklist.
     *
     * @param clazz the class to remove from blacklist
     * @throws NullPointerException if clazz is null
     * @return true if the class was removed, false if it wasn't blacklisted
     */
    public boolean removeClassFromBlacklist(@NotNull Class<?> clazz) {
        Objects.requireNonNull(clazz, "Class cannot be null");
        return blacklist.remove(clazz);
    }

    /**
     * Removes a class from the whitelist.
     *
     * @param clazz the class to remove from whitelist
     * @throws NullPointerException if clazz is null
     * @return true if the class was removed, false if it wasn't whitelisted
     */
    public boolean removeClassFromWhitelist(@NotNull Class<?> clazz) {
        Objects.requireNonNull(clazz, "Class cannot be null");
        return whitelist.remove(clazz);
    }

    /**
     * Checks if a class is blacklisted
     *
     * @param clazz the class to check
     * @throws NullPointerException if clazz is null
     * @return true if the class is blacklisted
     */
    public boolean isClassBlacklisted(@NotNull Class<?> clazz) {
        Objects.requireNonNull(clazz, "Class cannot be null");
        return blacklist.contains(clazz);
    }

    /**
     * Checks if a class is whitelisted
     *
     * @param clazz the class to check
     * @throws NullPointerException if clazz is null
     * @return true if the class is whitelisted
     */
    public boolean isClassWhitelisted(@NotNull Class<?> clazz) {
        Objects.requireNonNull(clazz, "Class cannot be null");
        return whitelist.contains(clazz);
    }

    /**
     * Clears all classes from the blacklist
     */
    public void clearBlacklist() {
        blacklist.clear();
    }

    /**
     * Clears all classes from the whitelist
     */
    public void clearWhitelist() {
        whitelist.clear();
    }

    /**
     * Gets the current serialization pool
     *
     * @return the current pool, or null if not initialized
     */
    public ISerDesPool getPool() {
        return pool;
    }

    /**
     * Returns the number of blacklisted classes
     *
     * @return the size of the blacklist
     */
    public int getBlacklistSize() {
        return blacklist.size();
    }

    /**
     * Returns the number of whitelisted classes
     *
     * @return the size of the whitelist
     */
    public int getWhitelistSize() {
        return whitelist.size();
    }
}