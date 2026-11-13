#ifndef OCM_LOCAL_ENGINE_H
#define OCM_LOCAL_ENGINE_H

#include <cstdint>
#include <string>

namespace ocm {

/**
 * Handle behind which an on-device model lives.
 *
 * Deliberately opaque: the JNI layer holds one of these per loaded model and the
 * Kotlin side holds a long. Nothing about llama.cpp's types leaks into the app.
 */
struct LocalModel;

struct GenParams {
    float temperature = 0.7f;
    int32_t topK = 40;
    float topP = 0.95f;
    int32_t maxTokens = 512;
};

struct GenStats {
    int32_t promptTokens = 0;
    int32_t outputTokens = 0;
    double tokensPerSecond = 0.0;
    bool cancelled = false;
    bool ok = false;
    std::string error;
};

/** Callback shape mirrors the JNI sink object: one method, one string. */
using TokenCallback = void (*)(void* userData, const std::string& piece);

}  // namespace ocm

#endif  // OCM_LOCAL_ENGINE_H
