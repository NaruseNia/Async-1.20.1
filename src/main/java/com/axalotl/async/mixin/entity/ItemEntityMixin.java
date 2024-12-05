package com.axalotl.async.mixin.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    public ItemEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "tryMerge()V", at = @At(value = "HEAD"))
    private void lock(CallbackInfo ci) {
        lock.lock();
    }

    @Inject(method = "tryMerge()V", at = @At(value = "RETURN"))
    private void unlock(CallbackInfo ci) {
        lock.unlock();
    }

    @Override
    public void move(MovementType movementType, Vec3d movement) {
        synchronized (lock) {
            super.move(movementType, movement);
        }
    }
}
