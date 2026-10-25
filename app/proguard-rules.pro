# GhostDisplay is intended to be sideloaded; keep service entry points and
# the few hidden-API names we look up via reflection so a future minify
# build does not break the launch fallback ladder.
-keep class com.ghostdisplay.app.** { *; }
-keepclassmembers class android.app.ActivityManager {
    void moveTaskToDisplay(int, int);
}
-keepclassmembers class android.hardware.display.DisplayManager {
    android.hardware.display.VirtualDisplay createVirtualDisplay(...);
}
