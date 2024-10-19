package com.axalotl.async.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.server.world.OptionalChunk;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkManager;
import net.minecraft.world.chunk.ChunkStatus;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;


@Mixin(ServerChunkManager.class)
public abstract class ServerChunkManagerMixin extends ChunkManager {

    @Shadow
    @Final
    ServerWorld world;

    @Shadow
    @Final
    private long[] chunkPosCache;

    @Shadow
    @Final
    private ChunkStatus[] chunkStatusCache;

    @Shadow
    @Final
    private Chunk[] chunkCache;

    @Shadow
    protected abstract CompletableFuture<OptionalChunk<Chunk>> getChunkFuture(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create);

    @Shadow
    @Final
    public ServerChunkManager.MainThreadExecutor mainThreadExecutor;

    @Shadow
    protected abstract void putInCache(long pos, @Nullable Chunk chunk, ChunkStatus status);

    @Redirect(method = {"getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;", "getWorldChunk"}, at = @At(value = "FIELD", target = "Lnet/minecraft/server/world/ServerChunkManager;serverThread:Ljava/lang/Thread;", opcode = Opcodes.GETFIELD))
    private Thread overwriteServerThread(ServerChunkManager mgr) {
        return Thread.currentThread();
    }

    @WrapMethod(method = "putInCache")
    private synchronized void syncPutInCache(long pos, Chunk chunk, ChunkStatus status, Operation<Void> original) {
        original.call(pos, chunk, status);
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;", at = @At("HEAD"), cancellable = true)
    private void fixThread(int x, int z, ChunkStatus leastStatus, boolean create, CallbackInfoReturnable<Chunk> cir) {
        if (Thread.currentThread().getName().startsWith("Async")) {
            Chunk chunk2;
            Profiler profiler = this.world.getProfiler();
            profiler.visit("getChunk");
            long l = ChunkPos.toLong(x, z);
            for (int i = 0; i < 4; ++i) {
                if (l != this.chunkPosCache[i] || leastStatus != this.chunkStatusCache[i] || (chunk2 = this.chunkCache[i]) == null && create)
                    continue;
                cir.setReturnValue(chunk2);
                cir.cancel();
            }
            profiler.visit("getChunkCacheMiss");
            CompletableFuture<OptionalChunk<Chunk>> completableFuture = this.getChunkFuture(x, z, leastStatus, create);
            this.mainThreadExecutor.runTasks(completableFuture::isDone);
            OptionalChunk<Chunk> optionalChunk = completableFuture.join();
            chunk2 = optionalChunk.orElse(null);
            if (chunk2 == null && create) {
                throw Util.throwOrPause(new IllegalStateException("Chunk not there when requested: " + optionalChunk.getError()));
            } else {
                this.putInCache(l, chunk2, leastStatus);
                cir.setReturnValue(chunk2);
            }
            cir.cancel();
        }
    }
}