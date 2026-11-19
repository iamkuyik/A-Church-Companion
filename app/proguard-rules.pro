# Preserve generic type signatures & annotations required by Gson TypeToken
-keepattributes Signature
-keepattributes *Annotation*

# Preserve Gson-serialized data models
-keep class com.trust.acc.SavedManager$Item { *; }

# Keep Gson TypeToken subclasses
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep ViewBinding generated classes referenced reflectively
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static *** inflate(...);
    public static *** bind(...);
}
