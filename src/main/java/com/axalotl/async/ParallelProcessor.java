package com.axalotl.async;

import com.axalotl.async.config.AsyncConfig;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.vehicle.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ParallelProcessor {
    private static final Logger LOGGER = LogManager.getLogger();
    @Getter
    @Setter
    public static MinecraftServer server;
    public static AtomicInteger currentEnts = new AtomicInteger();
    private static final AtomicInteger ThreadPoolID = new AtomicInteger();
    public static ExecutorService tickPool;
    public static final Queue<CompletableFuture<Void>> entityTickFutures = new ConcurrentLinkedQueue<>();
    public static final Set<Class<?>> blacklistedClasses = ConcurrentHashMap.newKeySet();
    private static final Map<String, Set<Thread>> mcThreadTracker = new ConcurrentHashMap<>();
    public static final Map<Class<? extends Entity>, Boolean> modEntityCache = new ConcurrentHashMap<>();
    private static final Set<Class<?>> specialEntities = Set.of(
            PlayerEntity.class,
            ServerPlayerEntity.class,
            FallingBlockEntity.class
    );

    public static void setupThreadPool(int parallelism) {
        if (AsyncConfig.virtualThreads) {
            ThreadFactory factory = Thread.ofVirtual()
                    .name("Async-Tick-Pool-Thread-", 1)
                    .uncaughtExceptionHandler((thread, throwable) ->
                            LOGGER.error("Uncaught exception in virtual thread {}: {}", thread.getName(), throwable))
                    .factory();
            tickPool = Executors.newThreadPerTaskExecutor(factory);
        } else {
            ForkJoinPool.ForkJoinWorkerThreadFactory tickThreadFactory = p -> {
                ForkJoinWorkerThread factory = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(p);
                factory.setName("Async-Tick-Pool-Thread-" + ThreadPoolID.getAndIncrement());
                regThread("Async-Tick", factory);
                factory.setDaemon(true);
                factory.setContextClassLoader(Async.class.getClassLoader());
                return factory;
            };
            tickPool = new ForkJoinPool(parallelism, tickThreadFactory, (t, e) -> LOGGER.error("Uncaught exception in thread {}: {}", t.getName(), e), true);
        }
    }


    public static void regThread(String poolName, Thread thread) {
        mcThreadTracker.computeIfAbsent(poolName, s -> ConcurrentHashMap.newKeySet()).add(thread);
    }

    public static boolean isThreadPooled(String poolName, Thread t) {
        return mcThreadTracker.containsKey(poolName) && mcThreadTracker.get(poolName).contains(t);
    }

    public static boolean serverExecutionThreadPatch() {
        return isThreadPooled("Async-Tick", Thread.currentThread());
    }

    public static void callEntityTick(Consumer<Entity> tickConsumer, Entity entityIn) {
        if (shouldTickSynchronously(entityIn)) {
            tickSynchronously(tickConsumer, entityIn);
            return;
        }
        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> performAsyncEntityTick(tickConsumer, entityIn),
                tickPool
        );
        entityTickFutures.add(future);
    }

    private static boolean shouldTickSynchronously(Entity entity) {
        return AsyncConfig.disabled ||
                blacklistedClasses.contains(entity.getClass()) ||
                specialEntities.contains(entity.getClass()) ||
                tickPortalSynchronously(entity) ||
                entity instanceof AbstractMinecartEntity ||
                (AsyncConfig.disableTNT && entity instanceof TntEntity) ||
                isModEntity(entity);
    }

    private static boolean tickPortalSynchronously(Entity entity) {
        if (entity.portalManager != null && entity.portalManager.isInPortal()) {
            return true;
        }
        return entity instanceof ProjectileEntity;
    }


    private static void tickSynchronously(Consumer<Entity> tickConsumer, Entity entity) {
        try {
            tickConsumer.accept(entity);
        } catch (Exception e) {
            LOGGER.error("Error ticking entity {} synchronously",
                    entity.getType().getName(),
                    e);
        }
    }

    private static void performAsyncEntityTick(Consumer<Entity> tickConsumer, Entity entity) {
        try {
            currentEnts.incrementAndGet();
            tickConsumer.accept(entity);
        } catch (Exception e) {
            LOGGER.error("Error ticking entity {} asynchronously",
                    entity.getType().getName(),
                    e);
            blacklistedClasses.add(entity.getClass());
        } finally {
            currentEnts.decrementAndGet();
        }
    }

    public static void postEntityTick() {
        if (!AsyncConfig.disabled) {
            try {
                CompletableFuture<Void> allTasks = CompletableFuture
                        .allOf(entityTickFutures.toArray(new CompletableFuture[0]))
                        .orTimeout(5, TimeUnit.MINUTES);
                allTasks.join();
            } catch (CompletionException e) {
                LOGGER.error("Critical error during entity tick processing", e);
                server.shutdown();
            } finally {
                entityTickFutures.clear();
            }
        }
    }

    private static boolean isModEntity(Entity entityIn) {
        return modEntityCache.computeIfAbsent(entityIn.getClass(), clazz ->
                !clazz.getPackageName().startsWith("net.minecraft")
        );
    }

    public static void stop() {
        tickPool.shutdown();

        try {
            if (!tickPool.awaitTermination(60, TimeUnit.SECONDS)) {
                tickPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            tickPool.shutdownNow();
        }
    }
}