package com.axalotl.async.mixin.world;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.world.*;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.*;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;


@Mixin(value = ServerChunkManager.class, priority = 1500)
public abstract class ServerChunkManagerMixin extends ChunkManager {
    @Shadow
    @Final
    Thread serverThread;

    @Shadow
    public abstract @Nullable ChunkHolder getChunkHolder(long pos);

    @Shadow
    @Final
    public ThreadedAnvilChunkStorage threadedAnvilChunkStorage;

    @Inject(method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;", at = @At("HEAD"), cancellable = true)
    private void shortcutGetChunk(int x, int z, ChunkStatus leastStatus, boolean create, CallbackInfoReturnable<Chunk> cir) {
        if (Thread.currentThread() != this.serverThread) {
            final ChunkHolder holder = this.getChunkHolder(ChunkPos.toLong(x, z));
            if (holder != null) {
                final CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> future = holder.getFutureFor(leastStatus);
                Chunk chunk = future.getNow(ChunkHolder.UNLOADED_CHUNK).left().orElse(null);
                if (chunk instanceof WrapperProtoChunk readOnlyChunk) chunk = readOnlyChunk.getWrappedChunk();
                if (chunk != null) {
                    cir.setReturnValue(chunk);
                }
            }
        }
    }

    @Inject(method = "getWorldChunk", at = @At("HEAD"), cancellable = true)
    private void shortcutGetWorldChunk(int chunkX, int chunkZ, CallbackInfoReturnable<WorldChunk> cir) {
        if (Thread.currentThread() != this.serverThread) {
            final ChunkHolder holder = this.getChunkHolder(ChunkPos.toLong(chunkX, chunkZ));
            if (holder != null) {
                final CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> future = holder.getFutureFor(ChunkStatus.FULL);
                Chunk chunk = future.getNow(ChunkHolder.UNLOADED_CHUNK).left().orElse(null);
                if (chunk instanceof WorldChunk worldChunk) {
                    cir.setReturnValue(worldChunk);
                }
            }
        }
    }
}