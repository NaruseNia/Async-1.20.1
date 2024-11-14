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
    private <T> Collector<T, ?, List<T>> overwriteCollectToList(Collector<T, ?, List<T>> collector) {
        return ConcurrentCollections.toList();
    }

    @WrapMethod(method = "add")
    public synchronized boolean add(T e, Operation<Boolean> original) {
        return original.call(e);
    }

    @WrapMethod(method = "remove")
    public synchronized boolean remove(Object o, Operation<Boolean> original) {
        return original.call(o);
    }

    @WrapMethod(method = "size")
    public synchronized int size(Operation<Integer> original) {
        return original.call();
    }

    @WrapMethod(method = "getAllOfType")
    public synchronized <S> Collection<S> getAllOfType(Class<S> type, Operation<Collection<S>> original) {
        return original.call(type);
    }

    @WrapMethod(method = "contains")
    public synchronized boolean contains(Object o, Operation<Boolean> original) {
        return original.call(o);
    }

    @WrapMethod(method = "copy")
    public synchronized List<T> contains(Operation<List<T>> original) {
        return original.call();
    }
}