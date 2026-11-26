# kotlinx.serialization: keep generated serializers for @Serializable models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.fintrack.**$$serializer { *; }
-keepclassmembers class com.example.fintrack.** { *** Companion; }
-keepclasseswithmembers class com.example.fintrack.** { kotlinx.serialization.KSerializer serializer(...); }

# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
