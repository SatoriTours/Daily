# Koin DI
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# SQLDelight
-keep class com.dailysatori.shared.db.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.dailysatori.**$$serializer { *; }
-keepclassmembers class com.dailysatori.** {
    *** Companion;
}
-keepclasseswithmembers class com.dailysatori.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Navigation routes (Serializable)
-keep class com.dailysatori.core.navigation.** { *; }

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# LangChain4j OpenAI DTOs are serialized by Jackson at runtime. Release R8 must not
# rename or strip the annotated fields/methods used to build provider request JSON.
-keep class dev.langchain4j.model.openai.internal.** { *; }
-keep class com.fasterxml.jackson.annotation.** { *; }
-keep class com.fasterxml.jackson.databind.annotation.** { *; }

# LangChain4j ships a JDK HTTP client that references java.net.http, but Android uses OkHttp.
-dontwarn java.net.http.**

# General
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
