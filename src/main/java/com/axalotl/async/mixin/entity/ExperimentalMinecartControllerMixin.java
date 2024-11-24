package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.vehicle.ExperimentalMinecartController;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ExperimentalMinecartController.class)
public class ExperimentalMinecartControllerMixin {
    @WrapMethod(method = "tick")
    private void tick(Operation<Void> original) {
        try {
            original.call();
        } catch (Throwable ignored) {
        }
    }
}
