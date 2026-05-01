# Keep rules for typical Flutter/Android builds
# Adjust as needed for your app

# Flutter
-keep class io.flutter.** { *; }
-dontwarn io.flutter.**

# ObjectBox
-keep class io.objectbox.** { *; }
-dontwarn io.objectbox.**

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Okio/OkHttp (if any transitive)
-dontwarn okhttp3.**
-dontwarn okio.**

# AndroidX
-dontwarn androidx.**
-keep class androidx.** { *; }
