# RiviumSync SDK Consumer ProGuard Rules
# Automatically applied to apps consuming this SDK

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# ---- Gson ----
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---- Gson generic types ----
# `object : TypeToken<Map<String, Any?>>() {}` needs the generic signatures and the TypeToken subclasses; R8 (full
# mode, the default since AGP 8) removes them, and release builds then fail with "TypeToken must be created with a
# type argument".
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# ---- RiviumSync SDK classes read and written by Gson ----
-keep class co.rivium.sync.** { *; }

# ---- Paho MQTT ----
-keep class org.eclipse.paho.client.mqttv3.** { *; }
-dontwarn org.eclipse.paho.client.mqttv3.**

# ---- Room Database ----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.**

# ---- Kotlin Coroutines ----
-dontwarn kotlinx.coroutines.**
