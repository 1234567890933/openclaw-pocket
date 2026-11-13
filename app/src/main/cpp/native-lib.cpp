#include <jni.h>

#include <string>
#include <vector>

#include "sha256.h"
#include "vault_gate.h"

namespace {

std::string toStdString(JNIEnv* env, jbyteArray array) {
    if (array == nullptr) return {};
    const jsize len = env->GetArrayLength(array);
    std::vector<uint8_t> buffer(static_cast<size_t>(len));
    env->GetByteArrayRegion(array, 0, len, reinterpret_cast<jbyte*>(buffer.data()));
    return std::string(reinterpret_cast<const char*>(buffer.data()), buffer.size());
}

std::string jstringToStd(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string out(chars == nullptr ? "" : chars);
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return out;
}

jbyteArray toJByteArray(JNIEnv* env, const std::string& data) {
    jbyteArray out = env->NewByteArray(static_cast<jsize>(data.size()));
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(data.size()),
                            reinterpret_cast<const jbyte*>(data.data()));
    return out;
}

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_openclaude_mobile_data_secure_NativeHasher_sha256Hex(JNIEnv* env, jobject, jbyteArray input) {
    const std::string digest = ocm::sha256Hex(toStdString(env, input));
    return env->NewStringUTF(digest.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_openclaude_mobile_data_secure_NativeHasher_sha256HexSalted(
    JNIEnv* env, jobject, jstring salt, jstring input) {
    const std::string digest = ocm::sha256HexSalted(jstringToStd(env, salt), jstringToStd(env, input));
    return env->NewStringUTF(digest.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_openclaude_mobile_data_secure_NativeHasher_constantTimeEquals(
    JNIEnv* env, jobject, jbyteArray a, jbyteArray b) {
    const std::string left = toStdString(env, a);
    const std::string right = toStdString(env, b);
    const bool equal = ocm::constantTimeEquals(
        reinterpret_cast<const uint8_t*>(left.data()), left.size(),
        reinterpret_cast<const uint8_t*>(right.data()), right.size());
    return equal ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_openclaude_mobile_data_secure_NativeHasher_hmacSha256Hex(
    JNIEnv* env, jobject, jbyteArray key, jbyteArray message) {
    const std::string digest = ocm::hmacSha256Hex(toStdString(env, key), toStdString(env, message));
    return env->NewStringUTF(digest.c_str());
}

/**
 * Digests a payload in fixed-size chunks and returns the digest of the
 * concatenated chunk digests. Used to fingerprint large runtime archives without
 * holding them in memory on a phone.
 */
JNIEXPORT jstring JNICALL
Java_com_openclaude_mobile_data_secure_NativeHasher_streamingDigestChunks(
    JNIEnv* env, jobject, jbyteArray input, jint chunkSize) {
    const std::string data = toStdString(env, input);
    const size_t step = chunkSize > 0 ? static_cast<size_t>(chunkSize) : 8192;

    std::string accumulator;
    for (size_t offset = 0; offset < data.size(); offset += step) {
        const size_t take = (offset + step <= data.size()) ? step : data.size() - offset;
        accumulator += ocm::sha256Hex(data.substr(offset, take));
    }
    if (data.empty()) accumulator = ocm::sha256Hex(std::string());
    return env->NewStringUTF(ocm::sha256Hex(accumulator).c_str());
}

JNIEXPORT jstring JNICALL
Java_com_openclaude_mobile_data_secure_NativeHasher_buildFingerprint(JNIEnv* env, jobject) {
    // Not a secret; a stable id for the native core so bug reports can be matched.
    const std::string material = std::string("ocmcore/1.0/") + __DATE__;
    return env->NewStringUTF(ocm::sha256Hex(material).substr(0, 16).c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_openclaude_mobile_data_secure_NativeHasher_validatePhrase(
    JNIEnv* env, jobject, jstring phrase) {
    return ocm::validatePhrase(jstringToStd(env, phrase)).checksumOk ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
