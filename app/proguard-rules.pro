# Keep the JNI surface used by the vault.
-keepclasseswithmembernames class com.openclaude.mobile.data.secure.NativeHasher { native <methods>; }

# kotlinx.serialization models used by the agent protocol
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.openclaude.mobile.data.agent.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.openclaude.mobile.data.agent.**$$serializer { *; }

-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
