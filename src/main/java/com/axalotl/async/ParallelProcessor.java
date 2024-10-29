package com.axalotl.async;

import com.axalotl.async.serdes.SerDesHookTypes;
import com.axalotl.async.serdes.SerDesRegistry;
import com.axalotl.async.serdes.filter.ISerDesFilter;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
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
    protected static MinecraftServer server;
    protected static ExecutorService tickPool;
    private static final Queue<CompletableFuture<Void>> entityTickFutures = new ConcurrentLinkedQueue<>();
    protected static AtomicInteger ThreadPoolID = new AtomicInteger();
    @Getter
    public static AtomicInteger currentEnts = new AtomicInteger();
    private static final Map<Class<? extends Entity>, Boolean> modEntityCache = new ConcurrentHashMap<>();
    private static final Set<Class<?>> specialEntities = Set.of(
            PlayerEntity.class,
            ServerPlayerEntity.class,
            FallingBlockEntity.class
    );

    static Map<String, Set<Thread>> mcThreadTracker = new ConcurrentHashMap<>();

    public static void setupThreadPool(int parallelism) {
        if (Async.config.virtualThreads) {
            ThreadFactory factory = Thread.ofVirtual()
                    .name("Async-Tick-Pool-Thread-", 0)
                    .uncaughtExceptionHandler((thread, throwable) ->
                            LOGGER.error("Error in virtual thread {}: {}", thread.getName(), throwable))
                    .factory();
            tickPool = Executors.newThreadPerTaskExecutor(factory);
        } else {
            ForkJoinPool.ForkJoinWorkerThreadFactory tickThreadFactory = p -> {
                ForkJoinWorkerThread forkJoinWorkerThread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(p);
                forkJoinWorkerThread.setName("Async-Tick-Pool-Thread-" + ThreadPoolID.getAndIncrement());
                regThread("Async-Tick", forkJoinWorkerThread);
                forkJoinWorkerThread.setDaemon(true);
                forkJoinWorkerThread.setContextClassLoader(Async.class.getClassLoader());
                return forkJoinWorkerThread;
            };
            tickPool = new ForkJoinPool(parallelism, tickThreadFactory, (t, e) -> LOGGER.error("Error on create Async tickPool", e), true);
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

    public static void callEntityTick(Consumer<Entity> tickConsumer, Entity entityIn, ServerWorld serverworld) {
        if (shouldTickSynchronously(entityIn)) {
            tickSynchronously(tickConsumer, entityIn);
            return;
        }
        ChunkPos entityChunkPos = entityIn.getChunkPos();
        if (isChunkSafe(serverworld, entityChunkPos)) {
            tickSynchronously(tickConsumer, entityIn);
            return;
        }

        CompletableFuture<Void> future = CompletableFuture.runAsync(
            () -> performAsyncEntityTick(tickConsumer, entityIn, serverworld),
            tickPool
        );
        entityTickFutures.add(future);
    }

    private static boolean shouldTickSynchronously(Entity entity) {
        return Async.config.disabled ||
                Async.config.disableEntity ||
                isModEntity(entity) ||
                entity instanceof AbstractMinecartEntity ||
                specialEntities.contains(entity.getClass()) ||
                (Async.config.disableTNT && entity instanceof TntEntity) ||
                (entity.portalManager != null && entity.portalManager.isInPortal());
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

    private static boolean isChunkSafe(ServerWorld world, ChunkPos pos) {
        try {
            Chunk chunk = world.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
            return chunk == null || !chunk.getStatus().isAtLeast(ChunkStatus.FULL);
        } catch (Exception e) {
            LOGGER.error("Error checking chunk safety at {}", pos, e);
            return true;
        }
    }

    private static void performAsyncEntityTick(Consumer<Entity> tickConsumer, Entity entity, ServerWorld serverworld) {
        try {
            currentEnts.incrementAndGet();
            
            ChunkPos entityChunkPos = entity.getChunkPos();
            if (isChunkSafe(serverworld, entityChunkPos)) {
                tickSynchronously(tickConsumer, entity);
                return;
            }

            final ISerDesFilter filter = SerDesRegistry.getFilter(SerDesHookTypes.EntityTick, entity.getClass());

            if (filter != null) {
                filter.serialise(
                        () -> tickConsumer.accept(entity),
                        entity,
                        entity.getBlockPos(),
                        serverworld,
                        SerDesHookTypes.EntityTick
                );
            } else {
                tickConsumer.accept(entity);
            }
        } finally {
            currentEnts.decrementAndGet();
        }
    }

    public static void postEntityTick() {
        if (!Async.config.disabled && !Async.config.disableEntity) {
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