package com.axalotl.async.serdes.filter;

import com.axalotl.async.serdes.ISerDesHookType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Filter implementation for vanilla Minecraft classes.
 * Provides direct execution of tasks and whitelisting for Minecraft namespace classes.
 */
public final class VanillaFilter implements ISerDesFilter {

    private static final String MINECRAFT_PACKAGE_PREFIX = "net.minecraft";

    /**
     * Executes the task directly without serialization.
     * This implementation maintains vanilla behavior for Minecraft classes.
     *
     * @param task     the task to execute
     * @param obj      the object associated with the task
     * @param blockPos the block position
     * @param world    the world instance
     * @param hookType the serialization hook type
     * @throws NullPointerException if task is null
     */
    @Override
    public void serialise(@NotNull Runnable task,
                         @NotNull Object obj,
                         @NotNull BlockPos blockPos,
                         @NotNull World world,
                         @Nullable ISerDesHookType hookType) {
        Objects.requireNonNull(task, "Task cannot be null");
        Objects.requireNonNull(obj, "Object cannot be null");
        Objects.requireNonNull(blockPos, "BlockPos cannot be null");
        Objects.requireNonNull(world, "World cannot be null");

        task.run();
    }

    /**
     * Determines if a class should be handled as vanilla Minecraft content.
     * Classes in the net.minecraft namespace are considered vanilla.
     *
     * @param clazz the class to check
     * @return WHITELIST if class is in Minecraft namespace, UNKNOWN otherwise
     * @throws NullPointerException if clazz is null
     */
    @Override
    @NotNull
    public ClassMode getModeOnline(@NotNull Class<?> clazz) {
        Objects.requireNonNull(clazz, "Class cannot be null");
        
        return isMinecraftClass(clazz) ? ClassMode.WHITELIST : ClassMode.UNKNOWN;
    }

    /**
     * Checks if a class belongs to the Minecraft namespace.
     *
     * @param clazz the class to check
     * @return true if class is in Minecraft namespace
     */
    private boolean isMinecraftClass(@NotNull Class<?> clazz) {
        return clazz.getName().startsWith(MINECRAFT_PACKAGE_PREFIX);
    }

    /**
     * Checks if an object is an instance of a vanilla Minecraft class.
     *
     * @param obj the object to check
     * @return true if object is instance of Minecraft class
     * @throws NullPointerException if obj is null
     */
    public boolean isVanillaInstance(@NotNull Object obj) {
        Objects.requireNonNull(obj, "Object cannot be null");
        return isMinecraftClass(obj.getClass());
    }

    /**
     * Checks if a class name represents a vanilla Minecraft class.
     *
     * @param className fully qualified class name
     * @return true if class name is in Minecraft namespace
     * @throws NullPointerException if className is null
     */
    public boolean isVanillaClassName(@NotNull String className) {
        Objects.requireNonNull(className, "Class name cannot be null");
        return className.startsWith(MINECRAFT_PACKAGE_PREFIX);
    }
}