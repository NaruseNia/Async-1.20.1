package com.axalotl.async.mixin.entity;

import net.minecraft.util.TypeFilter;
import net.minecraft.util.annotation.Debug;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.Box;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.EntityTrackingStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

@Mixin(value = EntityTrackingSection.class, priority = 1500)
public abstract class EntityTrackingSectionMixin<T extends EntityLike> {
    @Shadow
    private EntityTrackingStatus status;
    @Unique
    private final List<T> collection = new CopyOnWriteArrayList<>();

    @Inject(method = "<init>", at = @At(value = "TAIL"))
    public void onInit(Class entityClass, EntityTrackingStatus status, CallbackInfo ci) {
        this.status = status;
    }

    @Overwrite
    public void add(T entity) {
        this.collection.add(entity);
    }

    @Overwrite
    public boolean remove(T entity) {
        return this.collection.remove(entity);
    }

    @Overwrite
    public LazyIterationConsumer.NextIteration forEach(Box box, LazyIterationConsumer<T> consumer) {
        for (T entityLike : this.collection) {
            if (entityLike.getBoundingBox().intersects(box) && consumer.accept(entityLike).shouldAbort()) {
                return LazyIterationConsumer.NextIteration.ABORT;
            }
        }

        return LazyIterationConsumer.NextIteration.CONTINUE;
    }

    @Overwrite
    public <U extends T> LazyIterationConsumer.NextIteration forEach(TypeFilter<T, U> type, Box box, LazyIterationConsumer<? super U> consumer) {
        Collection<? extends T> filteredCollection = this.collection.stream()
                .filter(type.getBaseClass()::isInstance)
                .toList();

        if (!filteredCollection.isEmpty()) {
            for (T entityLike : filteredCollection) {
                U entityLike2 = type.downcast(entityLike);
                if (entityLike2 != null && entityLike.getBoundingBox().intersects(box)
                        && consumer.accept(entityLike2).shouldAbort()) {
                    return LazyIterationConsumer.NextIteration.ABORT;
                }
            }
        }
        return LazyIterationConsumer.NextIteration.CONTINUE;
    }

    @Overwrite
    public boolean isEmpty() {
        return this.collection.isEmpty();
    }

    @Overwrite
    public Stream<T> stream() {
        return this.collection.stream();
    }

    @Overwrite
    public EntityTrackingStatus getStatus() {
        return this.status;
    }

    @Overwrite
    public EntityTrackingStatus swapStatus(EntityTrackingStatus status) {
        EntityTrackingStatus entityTrackingStatus = this.status;
        this.status = status;
        return entityTrackingStatus;
    }

    @Overwrite
    @Debug
    public int size() {
        return this.collection.size();
    }
}
