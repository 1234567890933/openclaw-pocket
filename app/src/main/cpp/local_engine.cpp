#include <jni.h>

#include <atomic>
#include <chrono>
#include <mutex>
#include <string>
#include <vector>

#include "local_engine.h"

#if OCM_HAS_LLAMA
#include "llama.h"
#endif

namespace {

constexpr const char* kSinkMethod = "emit";
constexpr const char* kSinkSignature = "(Ljava/lang/String;)V";

}  // namespace

// ---------------------------------------------------------------------------
// Engine state
// ---------------------------------------------------------------------------

struct EngineHandle {
    void* model = nullptr;
    void* ctx = nullptr;
    void* vocab = nullptr;
    std::atomic<bool> cancel{false};
    std::mutex mu;
    int32_t nThreads = 4;
    int32_t nCtx = 4096;
};

#if OCM_HAS_LLAMA

namespace {

std::string jstringToStd(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string out(chars == nullptr ? "" : chars);
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return out;
}

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_openclaude_mobile_data_local_LocalNative_available(JNIEnv*, jobject) {
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_openclaude_mobile_data_local_LocalNative_backendInfo(JNIEnv* env, jobject) {
    const std::string info = std::string("llama.cpp/") + "cpu-only" + " threads=" +
                             std::to_string(llama_max_devices());
    return env->NewStringUTF(info.c_str());
}

JNIEXPORT jlong JNICALL
Java_com_openclaude_mobile_data_local_LocalNative_load(
    JNIEnv* env, jobject, jstring path, jint nThreads, jint nCtx) {

    auto* handle = new EngineHandle();
    handle->nThreads = nThreads > 0 ? nThreads : 4;
    handle->nCtx = nCtx > 0 ? nCtx : 4096;

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    // A phone has no discrete GPU worth offloading to; keep everything on the CPU
    // so the memory map stays inside the app's heap budget.
    mparams.n_gpu_layers = 0;
    mparams.use_mmap = true;

    const std::string modelPath = jstringToStd(env, path);
    auto* model = llama_model_load_from_file(modelPath.c_str(), mparams);
    if (model == nullptr) {
        handle->model = nullptr;
        return reinterpret_cast<jlong>(handle);
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(handle->nCtx);
    cparams.n_batch = 256;
    cparams.n_threads = handle->nThreads;
    cparams.n_threads_batch = handle->nThreads;
    cparams.no_perf = true;

    auto* ctx = llama_init_from_model(model, cparams);
    if (ctx == nullptr) {
        llama_model_free(model);
        handle->model = nullptr;
        return reinterpret_cast<jlong>(handle);
    }

    handle->model = model;
    handle->ctx = ctx;
    handle->vocab = const_cast<llama_vocab*>(llama_model_get_vocab(model));
    return reinterpret_cast<jlong>(handle);
}

JNIEXPORT void JNICALL
Java_com_openclaude_mobile_data_local_LocalNative_unload(JNIEnv*, jobject, jlong ptr) {
    auto* handle = reinterpret_cast<EngineHandle*>(ptr);
    if (handle == nullptr) return;
    if (handle->ctx != nullptr) llama_free(static_cast<llama_context*>(handle->ctx));
    if (handle->model != nullptr) llama_model_free(static_cast<llama_model*>(handle->model));
    llama_backend_free();
    delete handle;
}

JNIEXPORT jint JNICALL
Java_com_openclaude_mobile_data_local_LocalNative_generate(
    JNIEnv* env, jobject, jlong ptr, jstring prompt,
    jfloat temperature, jint topK, jfloat topP, jint maxTokens, jobject sink) {

    auto* handle = reinterpret_cast<EngineHandle*>(ptr);
    if (handle == nullptr || handle->ctx == nullptr) return -1;

    std::lock_guard<std::mutex> lock(handle->mu);
    handle->cancel.store(false);

    auto* ctx = static_cast<llama_context*>(handle->ctx);
    auto* vocab = static_cast<llama_vocab*>(handle->vocab);

    const std::string text = jstringToStd(env, prompt);

    // Tokenise with room to spare; llama_tokenize returns the negative requirement
    // when the buffer is short, so a second pass is needed rather than a guess.
    std::vector<llama_token> tokens(text.size() + 32);
    int32_t n = llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()),
                               tokens.data(), static_cast<int32_t>(tokens.size()),
                               true, true);
    if (n < 0) {
        tokens.resize(static_cast<size_t>(-n));
        n = llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()),
                           tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    }
    if (n <= 0) return -2;
    tokens.resize(static_cast<size_t>(n));

    // Trim from the left if the prompt would overflow the context.
    const int32_t budget = handle->nCtx - maxTokens - 8;
    if (budget > 0 && n > budget) {
        tokens.erase(tokens.begin(), tokens.begin() + (n - budget));
        n = budget;
    }

    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    auto* smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    jclass sinkClass = env->GetObjectClass(sink);
    jmethodID emit = env->GetMethodID(sinkClass, kSinkMethod, kSinkSignature);
    if (emit == nullptr) {
        llama_sampler_free(smpl);
        return -3;
    }

    const auto started = std::chrono::steady_clock::now();
    int32_t produced = 0;

    // Prime the context with the prompt.
    llama_batch batch = llama_batch_get_one(tokens.data(), n);
    if (llama_decode(ctx, batch) != 0) {
        llama_sampler_free(smpl);
        return -4;
    }

    std::string pending;
    while (produced < maxTokens) {
        if (handle->cancel.load()) break;

        const llama_token id = llama_sampler_sample(smpl, ctx, -1);
        llama_sampler_accept(smpl, id);
        if (llama_vocab_is_eog(vocab, id)) break;

        char piece[256];
        const int32_t len = llama_token_to_piece(vocab, id, piece, sizeof(piece), 0, true);
        if (len > 0) {
            // Hold back a trailing partial UTF-8 sequence: emitting half a multi-byte
            // character produces a replacement glyph in the UI that never goes away.
            pending.append(piece, static_cast<size_t>(len));
            const size_t cut = pending.size();
            size_t safe = cut;
            while (safe > 0 && (static_cast<unsigned char>(pending[safe - 1]) & 0xC0) == 0x80) safe--;
            if (safe > 0) {
                const unsigned char lead = static_cast<unsigned char>(pending[safe - 1]);
                int need = 1;
                if ((lead & 0xE0) == 0xC0) need = 2;
                else if ((lead & 0xF0) == 0xE0) need = 3;
                else if ((lead & 0xF8) == 0xF0) need = 4;
                if (cut - (safe - 1) < static_cast<size_t>(need)) safe--;
            }
            if (safe > 0) {
                const std::string out = pending.substr(0, safe);
                pending.erase(0, safe);
                jstring jout = env->NewStringUTF(out.c_str());
                env->CallVoidMethod(sink, emit, jout);
                env->DeleteLocalRef(jout);
            }
        }

        ++produced;

        llama_batch next = llama_batch_get_one(const_cast<llama_token*>(&id), 1);
        if (llama_decode(ctx, next) != 0) break;
    }

    if (!pending.empty()) {
        jstring jout = env->NewStringUTF(pending.c_str());
        env->CallVoidMethod(sink, emit, jout);
        env->DeleteLocalRef(jout);
    }

    llama_sampler_free(smpl);
    return produced;
}

JNIEXPORT void JNICALL
Java_com_openclaude_mobile_data_local_LocalNative_cancel(JNIEnv*, jobject, jlong ptr) {
    auto* handle = reinterpret_cast<EngineHandle*>(ptr);
    if (handle != nullptr) handle->cancel.store(true);
}

JNIEXPORT jstring JNICALL
Java_com_openclaude_mobile_data_local_LocalNative_describe(JNIEnv* env, jobject, jlong ptr) {
    auto* handle = reinterpret_cast<EngineHandle*>(ptr);
    if (handle == nullptr || handle->model == nullptr) {
        return env->NewStringUTF("no model");
    }
    auto* model = static_cast<llama_model*>(handle->model);
    const std::string desc =
        "ctx=" + std::to_string(handle->nCtx) +
        " threads=" + std::to_string(handle->nThreads) +
        " train_ctx=" + std::to_string(llama_model_n_ctx_train(model)) +
        " params=" + std::to_string(llama_model_n_params(model));
    return env->NewStringUTF(desc.c_str());
}

}  // extern "C"

#else  // OCM_HAS_LLAMA

// Stub build: every entry point answers "unavailable" instead of failing to link.
extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_openclaude_mobile_data_local_LocalNative_available(JNIEnv*, jobject) {
    return JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_openclaude_mobile_data_local_LocalNative_backendInfo(JNIEnv* env, jobject) {
    return env->NewStringUTF("local engine not compiled in");
}

JNIEXPORT jlong JNICALL
Java_com_openclaude_mobile_data_local_LocalNative_load(JNIEnv*, jobject, jstring, jint, jint) {
    return 0;
}

JNIEXPORT void JNICALL
Java_com_openclaude_mobile_data_local_LocalNative_unload(JNIEnv*, jobject, jlong) {}

JNIEXPORT jint JNICALL
Java_com_openclaude_mobile_data_local_LocalNative_generate(
    JNIEnv*, jobject, jlong, jstring, jfloat, jint, jfloat, jint, jobject) {
    return -1;
}

JNIEXPORT void JNICALL
Java_com_openclaude_mobile_data_local_LocalNative_cancel(JNIEnv*, jobject, jlong) {}

JNIEXPORT jstring JNICALL
Java_com_openclaude_mobile_data_local_LocalNative_describe(JNIEnv* env, jobject, jlong) {
    return env->NewStringUTF("local engine not compiled in");
}

}  // extern "C"

#endif  // OCM_HAS_LLAMA
