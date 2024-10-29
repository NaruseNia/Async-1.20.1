package com.axalotl.async.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.chunk.ChunkManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ServerChunkManager.class, priority = 1500)
public abstract class ServerChunkManagerMixin extends ChunkManager {

    @Redirect(method = {"getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;", "getWorldChunk"}, at = @At(value = "FIELD", target = "Lnet/minecraft/server/world/ServerChunkManager;serverThread:Ljava/lang/Thread;", opcode = Opcodes.GETFIELD))
    private Thread overwriteServerThread(ServerChunkManager mgr) {
        return Thread.currentThread();
    }

    @WrapMethod(method = "broadcastUpdates")
    private synchronized void onBroadcastUpdates(Profiler profiler, Operation<Void> original) {
        original.call(profiler);
    }
}