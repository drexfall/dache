# R8 rules for the release APK. The default optimize rules come first; these cover what R8 can't see.

# kotlinx.serialization: serializers are looked up through generated companions and $$serializer classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
# Every @Serializable model in the app: snapshot cache, history, plugin manifests, settings.
-keep,includedescriptorclasses class dev.dache.**$$serializer { *; }
-keepclassmembers class dev.dache.** { *** Companion; }
-keepclasseswithmembers class dev.dache.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class dev.dache.**.ProviderIdSerializer { *; }

# Ktor finds its engine through ServiceLoader and reflects on a few internal classes.
-keep class * implements io.ktor.client.HttpClientEngineContainer
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**
-dontwarn kotlinx.atomicfu.**

# OkHttp and Okio ship their own consumer rules; silence the optional platform hooks they reference.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Tink: protobuf-lite messages are read through reflection on their fields.
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite { <fields>; }
-keep class com.google.crypto.tink.proto.** { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Coroutines debug hooks that aren't on Android.
-dontwarn java.lang.instrument.**
-dontwarn sun.misc.SignalHandler
