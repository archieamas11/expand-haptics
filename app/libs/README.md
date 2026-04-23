# Xposed API JAR

The Edge Haptics feature uses LSPosed / Xposed hooks. The module entry point
(`com.hapticks.app.edge.EdgeScrollHooks`) depends on the Xposed compile-time
API (`de.robv.android.xposed.*`). This dependency is **compile-only** — the
JAR is never packaged into the released APK, because at runtime LSPosed
injects the real Xposed classes into every target process.

## How to vendor the JAR

Download `api-82.jar` from the official Xposed API mirror (for example from
the `LSPosed` GitHub releases, or from the `de.robv.android.xposed:api:82`
Maven Central mirror) and drop it here as:

```
app/libs/api-82.jar
```

Gradle will pick it up automatically via the `compileOnly(files(...))` entry
in `app/build.gradle.kts`.

## What happens when the JAR is missing

If `api-82.jar` is not present, `app/build.gradle.kts` excludes
`EdgeScrollHooks.kt` from the compilation and the rest of the app still
builds normally. In that case `EdgeHapticsBridge.isAvailable()` always
returns `LSPOSED_INACTIVE` and the Edge Haptics UI is disabled, matching the
behavior the user sees on a non-rooted device.
