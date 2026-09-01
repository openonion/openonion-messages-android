# Project-specific R8 rules belong here. Keep this file intentionally minimal
# until a dependency or reflection boundary requires an explicit keep rule.
# Lazysodium and JNA resolve native methods through reflection.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * implements com.sun.jna.Library { *; }
-keep class com.goterl.lazysodium.** { *; }
-dontwarn java.awt.**

-keep @kotlinx.serialization.Serializable class ai.openonion.messages.** { *; }
