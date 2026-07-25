# Add project specific ProGuard rules here.

# Keep Room database entities and DAOs
-keep class com.example.database.** { *; }
-keep class com.example.model.** { *; }

# Keep accessibility services and handlers
-keep class com.example.accessibility.** { *; }
-keep class com.example.service.** { *; }
-keep class com.example.notification.** { *; }

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable

