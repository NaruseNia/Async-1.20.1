package com.axalotl.async.mixin.world;

import com.axalotl.async.ParallelProcessor;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.world.chunk.ChunkManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ServerChunkManager.class, priority = 1500)
public abstract class ServerChunkManagerMixin extends ChunkManager {
    @Unique
    private static final Logger LOGGER = LogManager.getLogger("Async-ChunkManager");

//    @Inject(method = "broadcastUpdates", at = @At("HEAD"), cancellable = true)
//    private void onBroadcastUpdates(CallbackInfo ci) {
//        ServerChunkManager self = (ServerChunkManager) (Object) this;
//        var updates = self.chunksToBroadcastUpdate;
//
//        if (updates != null && !updates.isEmpty()) {
//            ArrayList<ChunkHolder> safeUpdates = new ArrayList<>();
//
//            synchronized (updates) {
//                Iterator<ChunkHolder> iterator = updates.iterator();
//                while (iterator.hasNext()) {
//                    try {
//                        ChunkHolder holder = iterator.next();
//                        if (holder != null) {
//                            safeUpdates.add(holder);
//                        }
//                    } catch (Exception e) {
//                        LOGGER.warn("Error during chunk update collection", e);
//                        continue;
//                    }
//                    iterator.remove();
//                }
//            }
//
//            if (!safeUpdates.isEmpty()) {
//                for (ChunkHolder holder : safeUpdates) {
//                    try {
//                        WorldChunk chunk = holder.getWorldChunk();
//                        if (chunk != null) {
//                            holder.flushUpdates(chunk);
//                        }
//                    } catch (Exception e) {
//                        LOGGER.error("Error updating chunk", e);
//                    }
//                }
//            }
//        }
//
//        ci.cancel();
//    }

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
}