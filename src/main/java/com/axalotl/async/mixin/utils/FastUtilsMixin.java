package com.axalotl.async.mixin.utils;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = {
        Int2ObjectOpenHashMap.class,
        LongLinkedOpenHashSet.class,
        ObjectOpenCustomHashSet.class,
        Long2LongOpenHashMap.class,
        Long2ObjectLinkedOpenHashMap.class,
        ReferenceOpenHashSet.class,
        Reference2ReferenceArrayMap.class,
        Object2LongOpenHashMap.class
},
        targets = {
                "it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap$FastEntryIterator",
                "it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap$MapIterator",
                "it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap",
                "it.unimi.dsi.fastutil.objects.ReferenceArrayList$Spliterator",
                "it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet$SetIterator",
                "it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap",
                "it.unimi.dsi.fastutil.ints.IntArrayList",
                "it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap",
                "it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap$MapIterator"
        })
public class FastUtilsMixin {
}
