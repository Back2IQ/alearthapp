# Proguard / R8 Rules for Alert2IQ

# Keep WebBridge Javascript Interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class app.alert2iq.WebBridge { *; }

# Keep osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Keep OkHttp & Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Data Models
-keep class app.alert2iq.DisasterEvent { *; }
-keep class app.alert2iq.DisasterType { *; }
-keep class app.alert2iq.AlertLevel { *; }
-keep class app.alert2iq.DisastersFilter { *; }
-keep class app.alert2iq.Eew$Tier { *; }
