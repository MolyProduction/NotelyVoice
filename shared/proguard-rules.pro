# Keep JNI bridge – native methods must not be renamed
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Whisper JNI classes (accessed by name from native code)
-keep class com.whispercpp.whisper.** { *; }

# Keep Koin injection targets
-keep class com.module.notelycompose.** { *; }
-keep class de.molyecho.notlyvoice.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.** { *; }
-keepclassmembernames class kotlinx.coroutines.** { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }

# SQLDelight
-keep class com.squareup.sqldelight.** { *; }
