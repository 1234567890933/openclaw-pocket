package com.openclaude.mobile.data.secure

/**
 * JNI bridge into the native hashing core.
 *
 * Doing the digest and the constant-time comparison in C++ means the comparison
 * cannot be short-circuited by the JIT or a debugger breakpoint on a Kotlin
 * `==`, and the hot path for streaming digests stays off the JVM heap.
 */
object NativeHasher {

    init {
        System.loadLibrary("ocmcore")
    }

    external fun sha256Hex(input: ByteArray): String
    external fun sha256HexSalted(salt: String, input: String): String
    external fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean
    external fun hmacSha256Hex(key: ByteArray, message: ByteArray): String
    external fun streamingDigestChunks(input: ByteArray, chunkSize: Int): String

    /** Structural check: length, digits only, and the digit-sum checksum. */
    external fun validatePhrase(phrase: String): Boolean

    /** Returns a short fingerprint of the native core, useful in About screens. */
    external fun buildFingerprint(): String
}
