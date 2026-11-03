# Default ProGuard rules for Singlenote.
# R8 handles Compose, Room and Glance via their bundled consumer rules.

# Release builds have no logcat window, so the hidden diagnostics dialog reads
# stack traces back to the user. Keep line numbers and source file names so a
# minified trace still points at the exact Kotlin file and line.
-keepattributes SourceFile,LineNumberTable
