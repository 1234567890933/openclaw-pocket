# Keep the JNI surface reachable from the app module's native library.
-keepclasseswithmembernames class com.openclaude.mobile.data.secure.NativeHasher { native <methods>; }
-keep class com.openclaude.mobile.data.local.TokenSink { *; }
