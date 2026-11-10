# Share Now ProGuard / R8 Rules
-keep class com.nextai.sharenow.** { *; }
-keep class com.ushareit.** { *; }
-keep class com.google.android.gms.ads.** { *; }
-keep class com.android.billingclient.** { *; }
-keep class com.tencent.mmkv.** { *; }

# Preserve Native Methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve Data Models for Gson
-keepclassmembers class * implements java.io.Serializable { *; }
-keepclassmembers class ** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Firebase & Google Play Services
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Retrofit, OkHttp & Coroutines
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

