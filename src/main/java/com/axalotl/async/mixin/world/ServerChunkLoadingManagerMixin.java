package com.axalotl.async.mixin.world;

import com.axalotl.async.parallelised.fastutil.Int2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.world.ChunkLoadingManager;
import net.minecraft.world.chunk.AbstractChunkHolder;
import net.minecraft.world.chunk.ChunkLoader;
import net.minecraft.world.storage.StorageKey;
import net.minecraft.world.storage.VersionedChunkStorage;
import org.spongepowered.asm.mixin.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mixin(value = ServerChunkLoadingManager.class, priority = 1500)
public abstract class ServerChunkLoadingManagerMixin extends VersionedChunkStorage implements ChunkHolder.PlayersWatchingChunkProvider, ChunkLoadingManager {
    @Shadow
    @Final
    @Mutable
    private Int2ObjectMap<ServerChunkLoadingManager.EntityTracker> entityTrackers = new Int2ObjectConcurrentHashMap<>();

    @Shadow
    @Final
    @Mutable
    private List<ChunkLoader> loaders = new CopyOnWriteArrayList<>();

    public ServerChunkLoadingManagerMixin(StorageKey storageKey, Path directory, DataFixer dataFixer, boolean dsync) {
        super(storageKey, directory, dataFixer, dsync);
    }

    @WrapMethod(method = "release")
    private synchronized void release(AbstractChunkHolder chunkHolder, Operation<Void> original) {
        original.call(chunkHolder);
    }

    @WrapMethod(method = "loadEntity")
    private synchronized void loadEntity(Entity entity, Operation<Void> original) {
        original.call(entity);
    }

    @WrapMethod(method = "unloadEntity")
    private synchronized void unloadEntity(Entity entity, Operation<Void> original) {
        original.call(entity);
    }
}