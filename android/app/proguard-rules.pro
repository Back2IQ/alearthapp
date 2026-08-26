# Proguard / R8 Rules for AlearthApp

# Keep WebBridge Javascript Interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class app.alearthapp.WebBridge { *; }

# Keep osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Keep OkHttp & Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Data Models
-keep class app.alearthapp.DisasterEvent { *; }
-keep class app.alearthapp.DisasterType { *; }
-keep class app.alearthapp.AlertLevel { *; }
-keep class app.alearthapp.DisastersFilter { *; }
-keep class app.alearthapp.Eew$Tier { *; }
