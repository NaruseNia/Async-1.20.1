package com.axalotl.async.mixin.utils;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = {Int2ObjectOpenHashMap.class, LongLinkedOpenHashSet.class, ObjectOpenCustomHashSet.class, Long2LongOpenHashMap.class, Long2ObjectLinkedOpenHashMap.class, ReferenceOpenHashSet.class},
        targets = {"it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap$FastEntryIterator", "it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap$MapIterator"
        })
public class FastUtilsMixin {
}
