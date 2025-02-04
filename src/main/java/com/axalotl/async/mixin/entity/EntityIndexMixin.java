package com.axalotl.async.mixin.entity;

import com.axalotl.async.parallelised.ConcurrentCollections;
import com.axalotl.async.parallelised.fastutil.Int2ObjectConcurrentHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.EntityIndex;
import net.minecraft.world.entity.EntityLike;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

@Mixin(EntityIndex.class)
public abstract class EntityIndexMixin<T extends EntityLike> {
    @Shadow
    @Final
    @Mutable
    private Int2ObjectMap<T> idToEntity;

    @Shadow
    @Final
    @Mutable
    private Map<UUID, T> uuidToEntity;

    @Inject(method = "<init>",at = @At("TAIL"))
    private void replaceConVars(CallbackInfo ci)
    {
        idToEntity = new Int2ObjectConcurrentHashMap<>();
        uuidToEntity = ConcurrentCollections.newHashMap();
    }

    @Inject(method = "add", at = @At(value = "INVOKE", target = "Ljava/util/Map;containsKey(Ljava/lang/Object;)Z"), cancellable = true)
    private void skipWarn(T entity, CallbackInfo ci) {
        if (idToEntity.containsKey(entity.getId())) {
            uuidToEntity.remove(entity.getUuid());
            ci.cancel();
        }
    }
}