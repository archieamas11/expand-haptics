# Keep accessibility service class + constructors so the system can bind it by name.
-keep class com.hapticks.app.service.HapticsAccessibilityService { *; }
# Keep Application class for the same reason.
-keep class com.hapticks.app.HapticksApp { *; }

# Kotlin coroutines - internal debug metadata only; strip noisy probes from release.
-assumenosideeffects class kotlinx.coroutines.internal.FastServiceLoader { *; }

# Strip Log.v/d/i in release to shave binary + CPU cycles.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# --- LSPosed / libxposed ---
# EdgeScrollHooks is loaded reflectively by LSPosed via
# META-INF/xposed/java_init.list. R8 has no static reference to it, so pin it.
-keep class com.hapticks.app.edge.EdgeScrollHooks { *; }

# isModuleActive is the activation stub hooked by EdgeScrollHooks.
# LSPosed resolves it by name via XposedHelpers.findAndHookMethod — R8 must
# not rename, inline, or drop it.
-keepclassmembers,allowobfuscation class com.hapticks.app.edge.EdgeHapticsBridge {
    public static boolean isModuleActive();
    public static *** INSTANCE;
}
-keep class com.hapticks.app.edge.EdgeHapticsBridge {
    public static boolean isModuleActive();
}

# EdgeHapticReceiver is already pinned through the manifest, but keep its
# fields so the broadcast fallback path is not stripped.
-keep class com.hapticks.app.edge.EdgeHapticReceiver { *; }

# libxposed compile-time API is marked compileOnly, but keep any rule R8 might
# need to not choke on the missing symbols at optimization time.
-dontwarn de.robv.android.xposed.**
-dontwarn io.github.libxposed.api.**
