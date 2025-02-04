package com.axalotl.async.mixin.server;

import com.axalotl.async.ParallelProcessor;
import net.minecraft.server.dedicated.DedicatedServerWatchdog;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(DedicatedServerWatchdog.class)
public class DedicatedServerWatchdogMixin {
    @Inject(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/Bootstrap;println(Ljava/lang/String;)V"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void addCustomCrashReportion(CallbackInfo ci, long l, long m, long n, CrashReport crashReport, CrashReportSection crashReportSection) {
        CrashReportSection AsyncSection = crashReport.addElement("Async");
        AsyncSection.add("currentEnts", () -> ParallelProcessor.currentEntities.toString());
    }
}
