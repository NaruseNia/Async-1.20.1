package com.axalotl.async.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class AsyncConfig {
    public static final Logger LOGGER = LoggerFactory.getLogger("Async Config");

    private static final Supplier<CommentedFileConfig> configSupplier =
            () -> CommentedFileConfig.builder(FabricLoader.getInstance().getConfigDir().resolve("async.toml"))
                    .preserveInsertionOrder()
                    .sync()
                    .build();

    private static CommentedFileConfig CONFIG;

    public static boolean disabled;
    public static int paraMax;
    public static boolean virtualThreads;
    public static boolean disableTNT;

    public static void init() {
        LOGGER.info("Initializing AsyncConfig...");
        CONFIG = configSupplier.get();
        try {
            CONFIG.load();
            loadConfigValues();
            LOGGER.info("Configuration successfully loaded.");
        } catch (Throwable t) {
            LOGGER.error("Error loading configuration, setting default values");
            setDefaultValues();
            saveConfig();
        }
    }

    public static void saveConfig() {
        CONFIG.set("disabled", disabled);
        CONFIG.set("paraMax", paraMax);
        CONFIG.set("virtualThreads", virtualThreads);
        CONFIG.set("disableTNT", disableTNT);
        CONFIG.save();
        LOGGER.info("Configuration saved successfully.");
    }

    private static void loadConfigValues() {
        disabled = CONFIG.get("disabled");
        paraMax = CONFIG.get("paraMax");
        virtualThreads = CONFIG.get("virtualThreads");
        disableTNT = CONFIG.get("disableTNT");
    }

    private static void setDefaultValues() {
        disabled = false;
        paraMax = -1;
        virtualThreads = false;
        disableTNT = true;
        CONFIG.set("disabled", disabled);
        CONFIG.setComment("disabled", "Globally disable all toggleable functionality within the async system. Set to true to stop all asynchronous operations.");

        CONFIG.set("paraMax", paraMax);
        CONFIG.setComment("paraMax", "Maximum number of threads to use for parallel processing. Set to -1 to use all available CPU cores. "
                + "Note: If 'virtualThreads' is enabled, this setting will be ignored.");

        CONFIG.set("virtualThreads", virtualThreads);
        CONFIG.setComment("virtualThreads", "Enables Java 21+ virtual threads, allowing for a large number of lightweight threads. "
                + "If enabled, this may provide better scalability on systems with high concurrency needs.");

        CONFIG.set("disableTNT", disableTNT);
        CONFIG.setComment("disableTNT", "Disables TNT entity parallelization. Use this to prevent asynchronous processing of TNT-related tasks");
    }

    public static int getParallelism() {
        if (paraMax <= 1) {
            return Runtime.getRuntime().availableProcessors();
        }
        return org.joml.Math.max(2, org.joml.Math.min(Runtime.getRuntime().availableProcessors(), paraMax));
    }
}
