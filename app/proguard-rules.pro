# ProGuard / R8 rules for Amar Gallery
-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** { *; }
-dontwarn org.drinkless.tdlib.**

-keep class com.amar.gallery.util.NativeEngine { *; }
-keepclassmembers class com.amar.gallery.util.NativeEngine { *; }

-keep class com.amar.gallery.data.model.** { *; }
-keepclassmembers class com.amar.gallery.data.model.** { *; }

-keep class com.amar.gallery.data.db.** { *; }
-keepclassmembers class com.amar.gallery.data.db.** { *; }

-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

-keep class coil.** { *; }
-dontwarn coil.**

-dontwarn kotlinx.coroutines.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
