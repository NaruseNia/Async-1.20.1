package com.axalotl.async.serdes;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import com.axalotl.async.config.BlockEntityLists;
import com.axalotl.async.config.SerDesConfig;
import com.axalotl.async.serdes.filter.*;
import com.axalotl.async.serdes.pools.ChunkLockPool;
import com.axalotl.async.serdes.pools.ISerDesPool;
import com.axalotl.async.serdes.pools.PostExecutePool;
import com.axalotl.async.serdes.pools.SingleExecutionPool;

import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SerDesRegistry {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<Class<?>, ISerDesFilter> EMPTY_FILTER_MAP = new ConcurrentHashMap<>();
    private static final Set<Class<?>> EMPTY_CLASS_SET = ConcurrentHashMap.newKeySet();
    
    private static final Map<ISerDesHookType, Map<Class<?>, ISerDesFilter>> optimisedLookup = new ConcurrentHashMap<>();
    private static final Map<ISerDesHookType, Set<Class<?>>> whitelist = new ConcurrentHashMap<>();
    private static final Set<Class<?>> unknown = ConcurrentHashMap.newKeySet();
    private static final Set<ISerDesHookType> hookTypes = ConcurrentHashMap.newKeySet();
    private static final List<ISerDesFilter> filters = new ArrayList<>();
    private static final Map<String, ISerDesPool> poolRegistry = new ConcurrentHashMap<>();
    
    private static final ISerDesFilter DEFAULT_FILTER = new DefaultFilter();

    static {
        Collections.addAll(hookTypes, SerDesHookTypes.values());
    }

    private SerDesRegistry() {
        // Приватный конструктор для предотвращения создания экземпляров
    }

    public static void init() {
        initPools();
        initFilters();
        initLookup();
    }

    private static void initFilters() {
        filters.clear();
        
        // Высокоприоритетные фильтры
        addFilter(new VanillaFilter());
        addFilter(new LegacyFilter());
        
        // Фильтры из конфигурации
        for (SerDesConfig.FilterConfig config : SerDesConfig.getFilters()) {
            addFilter(new GenericConfigFilter(config));
        }
        
        // Низкоприоритетные фильтры
        addFilter(AutoFilter.singleton());
        addFilter(DEFAULT_FILTER);
        
        // Инициализация всех фильтров
        for (ISerDesFilter filter : filters) {
            filter.init();
        }
    }

    private static void addFilter(ISerDesFilter filter) {
        if (filter != null) {
            filters.add(filter);
        }
    }

    private static void initLookup() {
        optimisedLookup.clear();
        
        for (ISerDesFilter filter : filters) {
            Set<Class<?>> targets = filter.getTargets();
            Set<Class<?>> whitelistClasses = filter.getWhitelist();
            
            if (targets == null) targets = ConcurrentHashMap.newKeySet();
            if (whitelistClasses == null) whitelistClasses = ConcurrentHashMap.newKeySet();
            
            Map<ISerDesHookType, Set<Class<?>>> groupedWhitelist = groupClassesByHookType(whitelistClasses);
            
            for (ISerDesHookType hookType : hookTypes) {
                for (Class<?> targetClass : targets) {
                    if (hookType.isTargetable(targetClass)) {
                        optimisedLookup.computeIfAbsent(hookType, k -> new ConcurrentHashMap<>())
                            .put(targetClass, filter);
                        groupedWhitelist.getOrDefault(hookType, EMPTY_CLASS_SET).remove(targetClass);
                    }
                }
                
                whitelist.computeIfAbsent(hookType, k -> ConcurrentHashMap.newKeySet())
                    .addAll(groupedWhitelist.getOrDefault(hookType, EMPTY_CLASS_SET));
            }
        }
    }

    private static Map<ISerDesHookType, Set<Class<?>>> groupClassesByHookType(Set<Class<?>> classes) {
        Map<ISerDesHookType, Set<Class<?>>> grouped = new ConcurrentHashMap<>();
        
        for (Class<?> clazz : classes) {
            for (ISerDesHookType hookType : hookTypes) {
                if (hookType.isTargetable(clazz)) {
                    grouped.computeIfAbsent(hookType, k -> ConcurrentHashMap.newKeySet())
                        .add(clazz);
                }
            }
        }
        
        return grouped;
    }

    public static ISerDesFilter getFilter(ISerDesHookType hookType, Class<?> clazz) {
        if (whitelist.getOrDefault(hookType, EMPTY_CLASS_SET).contains(clazz)) {
            return null;
        }
        return optimisedLookup.getOrDefault(hookType, EMPTY_FILTER_MAP)
            .getOrDefault(clazz, DEFAULT_FILTER);
    }

    public static ISerDesPool getPool(String name) {
        return poolRegistry.get(name);
    }

    public static ISerDesPool getOrCreatePool(String name, Function<String, ISerDesPool> factory) {
        return poolRegistry.computeIfAbsent(name, factory);
    }

    public static ISerDesPool getOrCreatePool(String name, Supplier<ISerDesPool> supplier) {
        return getOrCreatePool(name, poolName -> {
            ISerDesPool pool = supplier.get();
            pool.init(poolName, new HashMap<>());
            return pool;
        });
    }

    private static void initPools() {
        poolRegistry.clear();
        
        // Стандартные пулы
        getOrCreatePool("LEGACY", ChunkLockPool::new);
        getOrCreatePool("SINGLE", SingleExecutionPool::new);
        getOrCreatePool("POST", () -> PostExecutePool.POOL);
        
        // Пулы из конфигурации
        List<SerDesConfig.PoolConfig> poolConfigs = SerDesConfig.getPools();
        if (poolConfigs != null) {
            for (SerDesConfig.PoolConfig config : poolConfigs) {
                createPoolFromConfig(config);
            }
        }
    }

    private static void createPoolFromConfig(SerDesConfig.PoolConfig config) {
        if (!poolRegistry.containsKey(config.getName())) {
            try {
                Class<?> poolClass = Class.forName(config.getClazz());
                Constructor<?> constructor = poolClass.getConstructor();
                Object poolInstance = constructor.newInstance();

                if (poolInstance instanceof ISerDesPool pool) {
                    pool.init(config.getName(), config.getInitParams());
                    poolRegistry.put(config.getName(), pool);
                }
            } catch (ClassNotFoundException | NoSuchMethodException | SecurityException |
                     InstantiationException | IllegalAccessException | IllegalArgumentException |
                     InvocationTargetException e) {
                LOGGER.error("Failed to create pool from config: " + config.getName(), e);
            }
        }
    }

    public static class DefaultFilter implements ISerDesFilter {
        private ISerDesPool chunkLockPool;
        private ISerDesPool.ISerDesOptions config;

        @Override
        public void init() {
            chunkLockPool = getOrCreatePool("LEGACY", ChunkLockPool::new);
            Map<String, Object> cfg = new HashMap<>();
            cfg.put("range", "1");
            config = chunkLockPool.compileOptions(cfg);
        }

        @Override
        public void serialise(Runnable task, Object obj, BlockPos pos, World world, ISerDesHookType hookType) {
            if (!unknown.contains(obj.getClass())) {
                ClassMode mode = ClassMode.UNKNOWN;
                
                for (ISerDesFilter filter : filters) {
                    ClassMode currentMode = filter.getModeOnline(obj.getClass());
                    if (currentMode.compareTo(mode) < 0) {
                        mode = currentMode;
                    }
                    if (mode == ClassMode.BLACKLIST) {
                        optimisedLookup.computeIfAbsent(hookType, i -> new ConcurrentHashMap<>())
                            .put(obj.getClass(), this);
                        filter.serialise(task, obj, pos, world, hookType);
                        return;
                    }
                }
                
                if (mode == ClassMode.WHITELIST) {
                    whitelist.computeIfAbsent(hookType, k -> ConcurrentHashMap.newKeySet())
                        .add(obj.getClass());
                    task.run();
                    return;
                }
                
                unknown.add(obj.getClass());
            }
            
            if (hookType.equals(SerDesHookTypes.TETick) && shouldFilterBlockEntity(obj)) {
                if (chunkLockPool == null) {
                    chunkLockPool = getOrCreatePool("LEGACY", ChunkLockPool::new);
                }
                chunkLockPool.serialise(task, obj, pos, world, config);
            } else {
                executeTaskSafely(task, obj);
            }
        }

        private boolean shouldFilterBlockEntity(Object blockEntity) {
            if (blockEntity instanceof PistonBlockEntity) {
                return true;
            }

            boolean isBlacklisted = BlockEntityLists.teBlackList.contains(blockEntity.getClass());
            if (!isBlacklisted && !blockEntity.getClass().getName().startsWith("net.minecraft.block.entity.")) {
                isBlacklisted = true;
            }

            return isBlacklisted && !BlockEntityLists.teWhiteList.contains(blockEntity.getClass());
        }

        private void executeTaskSafely(Runnable task, Object obj) {
            try {
                task.run();
            } catch (Exception e) {
                LOGGER.error("Exception running {} asynchronously", obj.getClass().getName(), e);
                LOGGER.error("Adding {} to blacklist", obj.getClass().getName());
                
                AutoFilter.singleton().addClassToBlacklist(obj.getClass());
                
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
            }
        }
    }
}