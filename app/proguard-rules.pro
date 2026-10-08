# Proguard rules for Core Player

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Moshi
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# Media3
-keep class androidx.media3.** { *; }
