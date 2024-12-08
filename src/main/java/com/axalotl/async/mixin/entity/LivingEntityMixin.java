package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    @Shadow private Optional<BlockPos> climbingPos;

    @Shadow protected abstract boolean canEnterTrapdoor(BlockPos pos, BlockState state);

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @WrapMethod(method = "onDeath")
    private synchronized void onDeath(DamageSource damageSource, Operation<Void> original) {
        original.call(damageSource);
    }


    @Overwrite
    public boolean isClimbing() {
        if (this.isSpectator()) {
            return false;
        } else {
            BlockPos blockPos = this.getBlockPos();
            BlockState blockState = this.getBlockStateAtPos();
            if (blockState == null) return false;
            if (blockState.isIn(BlockTags.CLIMBABLE)) {
                this.climbingPos = Optional.of(blockPos);
                return true;
            } else if (blockState.getBlock() instanceof TrapdoorBlock && this.canEnterTrapdoor(blockPos, blockState)) {
                this.climbingPos = Optional.of(blockPos);
                return true;
            } else {
                return false;
            }
        }
    }

}
