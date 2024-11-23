package com.axalotl.async.commands;

import com.axalotl.async.config.AsyncConfig;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.axalotl.async.ParallelProcessor;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.DecimalFormat;

import static com.axalotl.async.commands.AsyncCommand.prefix;
import static net.minecraft.server.command.CommandManager.literal;

public class StatsCommand {
    static final int samples = 100;
    static final int stepsPer = 35;
    static int[] maxThreads = new int[samples];
    static int currentSteps = 0;
    static int currentPos = 0;
    static int liveValues = 0;
    static int[] maxEntities = new int[samples];
    static int entityCurrentSteps = 0;
    static int entityCurrentPos = 0;
    static int entityLiveValues = 0;
    static Thread statsThread;
    static boolean resetThreadStats = false;

    public static LiteralArgumentBuilder<ServerCommandSource> registerStatus(LiteralArgumentBuilder<ServerCommandSource> root) {
        return root.then(literal("stats")
                .executes(cmdCtx -> {
                    String threadMessageString = "Current max threads: ";
                    MutableText message = prefix.copy()
                            .append(Text.literal(threadMessageString).styled(style -> style.withColor(Formatting.WHITE)))
                            .append(Text.literal(String.valueOf(new DecimalFormat("#.##").format(mean(maxThreads, liveValues)))).styled(style -> style.withColor(Formatting.GREEN)));
                    cmdCtx.getSource().sendFeedback(() -> message, true);
                    return 1;
                }));
    }

    public static void resetAll() {
        resetThreadStats = true;
    }

    public static float mean(int[] data, int max) {
        float total = 0;
        for (int i = 0; i < max; i++) {
            total += data[i];
        }
        total /= max;
        return total;
    }


    public static void runStatsThread() {
        statsThread = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(10);
                    if (resetThreadStats) {
                        maxThreads = new int[samples];
                        currentSteps = 0;
                        currentPos = 0;
                        liveValues = 0;
                        resetThreadStats = false;
                    }
                    if (!AsyncConfig.disabled) {
                        if (++currentSteps % stepsPer == 0) {
                            currentPos = (currentPos + 1) % samples;
                            liveValues = Math.min(liveValues + 1, samples);
                            maxThreads[currentPos] = 0;
                        }
                        int entities = ParallelProcessor.currentEntities.get();
                        maxThreads[currentPos] = Math.max(maxThreads[currentPos], entities);

                        if (++entityCurrentSteps % stepsPer == 0) {
                            entityCurrentPos = (entityCurrentPos + 1) % samples;
                            entityLiveValues = Math.min(entityLiveValues + 1, samples);
                            maxEntities[entityCurrentPos] = 0;
                        }
                        maxEntities[entityCurrentPos] = Math.max(maxEntities[entityCurrentPos], entities);
                    } else {
                        resetAll();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        statsThread.setDaemon(true);
        statsThread.setName("Async Stats Thread");
        statsThread.start();
    }
}
