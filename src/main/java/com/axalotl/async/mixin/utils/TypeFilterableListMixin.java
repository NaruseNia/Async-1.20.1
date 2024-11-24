package com.axalotl.async.mixin.utils;

import com.axalotl.async.parallelised.ConcurrentCollections;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.collection.TypeFilterableList;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collector;

@Mixin(value = TypeFilterableList.class, priority = 1500)
public abstract class TypeFilterableListMixin<T> extends AbstractCollection<T> {
    @Shadow
    @Final
    @Mutable
    private Map<Class<?>, List<T>> elementsByType = new ConcurrentHashMap<>();

    @Shadow
    @Final
    @Mutable
    private List<T> allElements = new CopyOnWriteArrayList<>();

    @ModifyArg(method = "method_15217", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"))
    private Collector<T, ?, List<T>> overwriteCollectToList(Collector<T, ?, List<T>> collector) {
        return ConcurrentCollections.toList();
    }
}