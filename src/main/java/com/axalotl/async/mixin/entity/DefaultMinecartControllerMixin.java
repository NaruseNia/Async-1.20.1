package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.vehicle.DefaultMinecartController;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DefaultMinecartController.class)
public class DefaultMinecartControllerMixin {
    @WrapMethod(method = "tick")
    private void tick(Operation<Void> original) {
        try {
            original.call();
        } catch (Throwable ignored) {
        }
    }
}
