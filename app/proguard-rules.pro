# ProGuard / R8 rules for Amar Gallery
-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** { *; }

-keep class com.amar.gallery.util.NativeEngine { *; }
-keepclassmembers class com.amar.gallery.util.NativeEngine { *; }

-dontwarn org.drinkless.tdlib.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

