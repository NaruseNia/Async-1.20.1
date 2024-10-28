package com.axalotl.async.mixin.world;

import com.axalotl.async.Async;
import com.axalotl.async.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.world.OptionalChunk;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.Profilers;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(value = ServerChunkManager.class, priority = 1500)
public abstract class ServerChunkManagerMixin extends ChunkManager {

    @Shadow
    @Final
    public ServerChunkManager.MainThreadExecutor mainThreadExecutor;

    @Shadow
    protected abstract CompletableFuture<OptionalChunk<Chunk>> getChunkFuture(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create);

    @Shadow
    protected abstract void putInCache(long pos, @Nullable Chunk chunk, ChunkStatus status);

    @Inject(method = "tickChunks*",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/Util;shuffle(Ljava/util/List;Lnet/minecraft/util/math/random/Random;)V"))
    private void preChunkTick(CallbackInfo ci) {
        ParallelProcessor.preChunkTick();
    }

    @Redirect(method = {"getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;", "getWorldChunk"}, at = @At(value = "FIELD", target = "Lnet/minecraft/server/world/ServerChunkManager;serverThread:Ljava/lang/Thread;", opcode = Opcodes.GETFIELD))
    private Thread overwriteServerThread(ServerChunkManager mgr) {
        return Thread.currentThread();
    }

    @WrapMethod(method = "broadcastUpdates")
    private synchronized void onBroadcastUpdates(Profiler profiler, Operation<Void> original) {
        original.call(profiler);
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;", at = @At("HEAD"), cancellable = true)
    private void fixThread(int x, int z, ChunkStatus leastStatus, boolean create, CallbackInfoReturnable<Chunk> cir) {
        if (Async.config.disabled) {
            if (ParallelProcessor.isThreadPooled("Main", Thread.currentThread())) {
                cir.setReturnValue(CompletableFuture.supplyAsync(() -> this.getChunk(x, z, leastStatus, create), this.mainThreadExecutor).join());
            }
            cir.setReturnValue(getChunk(x, z, leastStatus, create));
        }
        if (ParallelProcessor.isThreadPooled("Main", Thread.currentThread())) {
            cir.setReturnValue(CompletableFuture.supplyAsync(() -> this.getChunk(x, z, leastStatus, create), this.mainThreadExecutor).join());
        }
        Profiler profiler = Profilers.get();
        profiler.visit("getChunk");
        long i = ChunkPos.toLong(x, z);

        synchronized (this) {
            profiler.visit("getChunkCacheMiss");
            CompletableFuture<OptionalChunk<Chunk>> completableFuture = this.getChunkFuture(x, z, leastStatus, create);
            this.mainThreadExecutor.runTasks(completableFuture::isDone);
            OptionalChunk<Chunk> optionalChunk = completableFuture.join();
            Chunk chunk2 = optionalChunk.orElse(null);
            if (chunk2 == null && create) {
                throw Util.getFatalOrPause(new IllegalStateException("Chunk not there when requested: " + optionalChunk.getError()));
            } else {
                this.putInCache(i, chunk2, leastStatus);
                cir.setReturnValue(chunk2);
            }
        }
    }
}