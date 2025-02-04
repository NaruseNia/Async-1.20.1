package com.axalotl.async.mixin.entity;

import com.axalotl.async.config.AsyncConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @WrapMethod(method = "move")
    private synchronized void move(MovementType type, Vec3d movement, Operation<Void> original) {
        if (AsyncConfig.enableEntityMoveSync) {
            synchronized (lock) {
                original.call(type, movement);
            }
        } else {
            original.call(type, movement);
        }
    }

    @WrapMethod(method = "checkBlockCollision")
    private synchronized void checkBlockCollision(Operation<Void> original) {
        if (AsyncConfig.enableEntityMoveSync) {
            synchronized (lock) {
                original.call();
            }
        } else {
            original.call();
        }
    }

//    @WrapMethod(method = "tickBlockCollision()V")
//    private void tickBlockCollision(Operation<Void> original) {
//        if (AsyncConfig.enableEntityMoveSync) {
//            synchronized (lock) {
//                original.call();
//            }
//        } else {
//            original.call();
//        }
//    }

//    @WrapMethod(method = "tickBlockCollision(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;)V")
//    private void tickBlockCollision(Vec3d lastRenderPos, Vec3d pos, Operation<Void> original) {
//        if (AsyncConfig.enableEntityMoveSync) {
//            synchronized (lock) {
//                original.call(lastRenderPos, pos);
//            }
//        } else {
//            original.call(lastRenderPos, pos);
//        }
//    }

    @WrapMethod(method = "setRemoved")
    private synchronized void setRemoved(Entity.RemovalReason reason, Operation<Void> original) {
        original.call(reason);
    }
}