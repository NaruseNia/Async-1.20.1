package com.axalotl.async.serdes.pools;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;

public final class SingleExecutionPool implements ISerDesPool {
    
    private final Lock executionLock;
    private final AtomicInteger activeCount;
    private volatile boolean isInitialized;
    
    public SingleExecutionPool() {
        this(new ReentrantLock());
    }

    SingleExecutionPool(@NotNull Lock lock) {
        this.executionLock = Objects.requireNonNull(lock);
        this.activeCount = new AtomicInteger(0);
        this.isInitialized = true;
    }

    @Override
    public void serialise(@NotNull Runnable task,
                         @NotNull Object obj,
                         @NotNull BlockPos blockPos,
                         @NotNull World world,
                         @Nullable ISerDesOptions options) {
        Objects.requireNonNull(task);
        Objects.requireNonNull(obj);
        Objects.requireNonNull(blockPos);
        Objects.requireNonNull(world);
        
        if (!isInitialized) {
            throw new IllegalStateException("Pool not initialized");
        }

        try {
            executionLock.lock();
            activeCount.incrementAndGet();
            task.run();
        } finally {
            activeCount.decrementAndGet();
            executionLock.unlock();
        }
    }

    public int getActiveCount() {
        return activeCount.get();
    }

    public boolean isLocked() {
        if (executionLock instanceof ReentrantLock) {
            return ((ReentrantLock) executionLock).isLocked();
        }
        return false;
    }

    public void close() {
        isInitialized = false;
    }
}