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
public class EntityMixin {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @WrapMethod(method = "move")
    private synchronized void move(MovementType movementType, Vec3d movement, Operation<Void> original) {
        if (AsyncConfig.enableEntityMoveSync){
            synchronized (lock) {
                original.call(movementType, movement);
            }
        } else {
            original.call(movementType, movement);
        }
    }
}