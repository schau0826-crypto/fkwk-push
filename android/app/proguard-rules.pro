# Shizuku
-keep class rikka.shizuku.** { *; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.fkwk.push.** {
    *** Companion;
}
-keepclasseswithmembers class dev.fkwk.push.** {
    kotlinx.serialization.KSerializer serializer(...);
}
