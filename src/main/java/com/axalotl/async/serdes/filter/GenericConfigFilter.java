package com.axalotl.async.serdes.filter;

import com.axalotl.async.config.SerDesConfig;
import com.axalotl.async.serdes.ISerDesHookType;
import com.axalotl.async.serdes.SerDesRegistry;
import com.axalotl.async.serdes.pools.ISerDesPool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Configuration-based serialization/deserialization filter implementation.
 * Supports both direct class matching and pattern-based filtering.
 */
public final class GenericConfigFilter implements ISerDesFilter {
    
    private static final String WILDCARD_MARKER = "**";
    private static final String WILDCARD_TEMP = "+-/";
    private static final String CLASS_CHARS = "[A-Za-z0-9$]*";
    private static final String WILDCARD_ALL = ".*";
    private static final String PATTERN_START = "^";
    private static final String PATTERN_END = "$";
    
    private final SerDesConfig.FilterConfig config;
    private ISerDesPool primaryPool;
    private ISerDesPool.ISerDesOptions primaryOptions;

    private Set<Class<?>> whitelist;
    private Set<Class<?>> blacklist;
    private Pattern whitelistPattern;
    private Pattern blacklistPattern;

    /**
     * Creates a new filter with the specified configuration
     *
     * @param config filter configuration
     * @throws NullPointerException if config is null
     */
    public GenericConfigFilter(@NotNull SerDesConfig.FilterConfig config) {
        this.config = Objects.requireNonNull(config, "Filter configuration cannot be null");
    }

    @Override
    public void init() {
        initializePools();
        initializeWhitelist();
        initializeBlacklist();
    }

    private void initializePools() {
        String poolName = config.getPool();
        Objects.requireNonNull(poolName, "Pool name cannot be null");
        
        primaryPool = SerDesRegistry.getPool(poolName);
        if (primaryPool == null) {
            throw new IllegalStateException("Pool not found: " + poolName);
        }
        
        primaryOptions = primaryPool.compileOptions(config.getPoolParams());
    }

    private void initializeWhitelist() {
        List<String> whitelistConfig = config.getWhitelist();
        if (whitelistConfig != null && !whitelistConfig.isEmpty()) {
            whitelist = ConcurrentHashMap.newKeySet();
            List<String> patterns = new ArrayList<>();

            for (String entry : whitelistConfig) {
                processClassPattern(entry, whitelist, patterns);
            }

            if (!patterns.isEmpty()) {
                whitelistPattern = createPattern(patterns);
            }
        }
    }

    private void initializeBlacklist() {
        List<String> blacklistConfig = config.getBlacklist();
        if (blacklistConfig != null && !blacklistConfig.isEmpty()) {
            blacklist = ConcurrentHashMap.newKeySet();
            List<String> patterns = new ArrayList<>();

            for (String entry : blacklistConfig) {
                processClassPattern(entry, blacklist, patterns);
            }

            if (!patterns.isEmpty()) {
                blacklistPattern = createPattern(patterns);
            }
        }
    }

    private void processClassPattern(String entry, Set<Class<?>> classList, List<String> patterns) {
        try {
            Class<?> clazz = Class.forName(entry);
            classList.add(clazz);
        } catch (ClassNotFoundException e) {
            patterns.add(convertToRegexPattern(entry));
        }
    }

    private String convertToRegexPattern(String pattern) {
        return PATTERN_START + 
               pattern.replace(".", "\\.")
                     .replace(WILDCARD_MARKER, WILDCARD_TEMP)
                     .replace("*", CLASS_CHARS)
                     .replace(WILDCARD_TEMP, WILDCARD_ALL) + 
               PATTERN_END;
    }

    private Pattern createPattern(List<String> patterns) {
        return Pattern.compile(String.join("|", patterns));
    }

    @Override
    @Nullable
    public Set<Class<?>> getWhitelist() {
        return whitelist != null ? Collections.unmodifiableSet(whitelist) : null;
    }

    @Override
    @Nullable
    public Set<Class<?>> getTargets() {
        return blacklist != null ? Collections.unmodifiableSet(blacklist) : null;
    }

    @Override
    @NotNull
    public ClassMode getModeOnline(@NotNull Class<?> clazz) {
        Objects.requireNonNull(clazz, "Class cannot be null");
        String className = clazz.getName();

        if (blacklistPattern != null && blacklistPattern.matcher(className).find()) {
            return ClassMode.BLACKLIST;
        }
        if (whitelistPattern != null && whitelistPattern.matcher(className).find()) {
            return ClassMode.WHITELIST;
        }
        return ClassMode.UNKNOWN;
    }

    @Override
    public void serialise(@NotNull Runnable task,
                         @NotNull Object obj,
                         @NotNull BlockPos blockPos,
                         @NotNull World world,
                         @Nullable ISerDesHookType hookType) {
        Objects.requireNonNull(task, "Task cannot be null");
        Objects.requireNonNull(obj, "Object cannot be null");
        Objects.requireNonNull(blockPos, "BlockPos cannot be null");
        Objects.requireNonNull(world, "World cannot be null");
        
        if (primaryPool == null) {
            throw new IllegalStateException("Filter has not been initialized");
        }
        
        primaryPool.serialise(task, hookType, blockPos, world, primaryOptions);
    }

    /**
     * Gets the current filter configuration
     *
     * @return the filter configuration
     */
    @NotNull
    public SerDesConfig.FilterConfig getConfig() {
        return config;
    }

    /**
     * Gets the primary serialization pool
     *
     * @return the primary pool
     * @throws IllegalStateException if the filter has not been initialized
     */
    @NotNull
    public ISerDesPool getPrimaryPool() {
        if (primaryPool == null) {
            throw new IllegalStateException("Filter has not been initialized");
        }
        return primaryPool;
    }

    /**
     * Checks if the given class matches any pattern in either whitelist or blacklist
     *
     * @param className fully qualified class name
     * @return true if the class matches any pattern
     */
    public boolean matchesAnyPattern(@NotNull String className) {
        Objects.requireNonNull(className, "Class name cannot be null");
        
        return (whitelistPattern != null && whitelistPattern.matcher(className).find()) ||
               (blacklistPattern != null && blacklistPattern.matcher(className).find());
    }

    /**
     * Gets all compiled patterns as strings
     *
     * @return map of pattern type to pattern string
     */
    @NotNull
    public Map<String, String> getCompiledPatterns() {
        Map<String, String> patterns = new HashMap<>();
        if (whitelistPattern != null) {
            patterns.put("whitelist", whitelistPattern.pattern());
        }
        if (blacklistPattern != null) {
            patterns.put("blacklist", blacklistPattern.pattern());
        }
        return patterns;
    }
}