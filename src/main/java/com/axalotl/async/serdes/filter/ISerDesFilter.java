package com.axalotl.async.serdes.filter;

import com.axalotl.async.serdes.ISerDesHookType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

/**
 * Interface for serialization/deserialization filters.
 * Provides methods for controlling the serialization process and managing
 * class filtering through blacklists and whitelists.
 */
public interface ISerDesFilter {

    /**
     * Performs the serialization of a task.
     *
     * @param task    the task to be serialized
     * @param obj     the object associated with the task
     * @param bp      the block position where the task is being performed
     * @param w       the world in which the task is being performed
     * @param hookType the type of serialization hook to use
     * @throws NullPointerException if task, obj, bp, or w is null
     */
    void serialise(@Nonnull Runnable task, 
                  @Nonnull Object obj, 
                  @Nonnull BlockPos bp, 
                  @Nonnull World w, 
                  @Nullable ISerDesHookType hookType);

    /**
     * Returns the set of blacklisted classes.
     * These classes will be excluded from serialization.
     *
     * @return set of blacklisted classes, or null if no blacklist exists
     */
    @Nullable
    default Set<Class<?>> getTargets() {
        return null;
    }

    /**
     * Initializes the filter.
     * This may include optimization steps like looking up pools pre-emptively,
     * generating pool configs, etc.
     * This method is invoked after pools are initialized.
     */
    default void init() {
        // Default implementation does nothing
    }

    /**
     * Returns the set of whitelisted classes.
     * Only these classes will be included in serialization if whitelist exists.
     *
     * @return set of whitelisted classes, or null if no whitelist exists
     */
    @Nullable
    default Set<Class<?>> getWhitelist() {
        return null;
    }

    /**
     * Represents different modes of class filtering.
     */
    enum ClassMode {
        /**
         * Class is explicitly blacklisted
         */
        BLACKLIST,

        /**
         * Class is explicitly whitelisted
         */
        WHITELIST,

        /**
         * Class status is not explicitly defined
         */
        UNKNOWN
    }

    /**
     * Determines the current filtering mode for a given class.
     * This method should be fast as it may be called frequently during runtime.
     *
     * @param clazz the class to check
     * @return the filtering mode for the class
     * @throws NullPointerException if clazz is null
     */
    @Nonnull
    default ClassMode getModeOnline(@Nonnull Class<?> clazz) {
        return ClassMode.UNKNOWN;
    }

    /**
     * Checks if a class is blacklisted.
     * Default implementation based on getTargets().
     *
     * @param clazz the class to check
     * @return true if the class is blacklisted
     */
    default boolean isBlacklisted(@Nonnull Class<?> clazz) {
        Set<Class<?>> targets = getTargets();
        return targets != null && targets.contains(clazz);
    }

    /**
     * Checks if a class is whitelisted.
     * Default implementation based on getWhitelist().
     *
     * @param clazz the class to check
     * @return true if the class is whitelisted
     */
    default boolean isWhitelisted(@Nonnull Class<?> clazz) {
        Set<Class<?>> whitelist = getWhitelist();
        return whitelist != null && whitelist.contains(clazz);
    }

    /**
     * Checks if serialization is allowed for the given class.
     * Default implementation based on blacklist and whitelist logic.
     *
     * @param clazz the class to check
     * @return true if serialization is allowed
     */
    default boolean isSerializationAllowed(@Nonnull Class<?> clazz) {
        Set<Class<?>> whitelist = getWhitelist();
        if (whitelist != null) {
            return whitelist.contains(clazz);
        }
        
        Set<Class<?>> blacklist = getTargets();
        if (blacklist != null) {
            return !blacklist.contains(clazz);
        }
        
        return true;
    }
}