package com.axalotl.async.parallelised;

import com.mojang.datafixers.DataFixer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkStatusChangeListener;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.LevelStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class SafeChunkAccess extends ServerChunkManager {
    private final ConcurrentHashMap<ChunkPos, WorldChunk> chunkCache = new ConcurrentHashMap<>();
    private final Logger LOGGER = LogManager.getLogger();
    public SafeChunkAccess(ServerWorld world,
                           LevelStorage.Session session,
                           DataFixer dataFixer,
                           StructureTemplateManager structureManager,
                           Executor workerExecutor,
                           ChunkGenerator chunkGenerator,
                           int viewDistance,
                           int simulationDistance,
                           boolean dsync,
                           WorldGenerationProgressListener worldGenerationProgressListener,
                           ChunkStatusChangeListener chunkStatusChangeListener,
                           Supplier<PersistentStateManager> persistentStateManagerFactory) {

        super(world, session, dataFixer, structureManager, workerExecutor,
                chunkGenerator, viewDistance, simulationDistance, dsync,
                worldGenerationProgressListener, chunkStatusChangeListener,
                persistentStateManagerFactory);

        ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "Async-Chunk-Cache-Cleanup");
            thread.setDaemon(true);
            return thread;
        });

        cleanupExecutor.scheduleAtFixedRate(
                this::clearCache,
                30, 30, TimeUnit.SECONDS
        );
    }

    @Override
    @Nullable
    public Chunk getChunk(int chunkX, int chunkZ, ChunkStatus requiredStatus, boolean load) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (requiredStatus == ChunkStatus.FULL) {
            WorldChunk cachedChunk = chunkCache.get(pos);
            if (cachedChunk == null) {
                Chunk chunk = super.getChunk(chunkX, chunkZ, requiredStatus, load);
                if (chunk instanceof WorldChunk) {
                    cachedChunk = (WorldChunk) chunk;
                    chunkCache.put(pos, cachedChunk);
                }
            }
            return cachedChunk;
        }
        return super.getChunk(chunkX, chunkZ, requiredStatus, load);
    }

    @Nullable
    @Override
    public WorldChunk getWorldChunk(int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        return chunkCache.computeIfAbsent(pos, p -> super.getWorldChunk(chunkX, chunkZ));
    }

    public void clearCache() {
        try {
            chunkCache.clear();
        } catch (Exception e) {
            LOGGER.error("Error clearing chunk cache", e);
        }
    }
}