
#include <jni.h>
#include "llama.h"

#include <algorithm>
#include <mutex>
#include <string>
#include <vector>

static llama_model* g_model = nullptr;
static std::mutex g_mutex;

extern "C"
JNIEXPORT jstring JNICALL
Java_com_pocketai_app_MainActivity_nativeGetBackendInfo(
        JNIEnv* env, jobject) {
    const char* info = llama_print_system_info();
    return env->NewStringUTF(info ? info : "llama.cpp loaded");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_pocketai_app_MainActivity_nativeLoadModel(
        JNIEnv* env, jobject, jstring jpath) {
    if (!jpath) {
        return env->NewStringUTF("ERROR: No model path provided.");
    }

    const char* path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) {
        return env->NewStringUTF("ERROR: Could not read model path.");
    }

    std::lock_guard<std::mutex> lock(g_mutex);

    llama_backend_init();

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;

    llama_model* candidate = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);

    if (!candidate) {
        return env->NewStringUTF(
            "ERROR: Model could not be loaded. Check the file and storage.");
    }

    if (g_model) {
        llama_model_free(g_model);
    }
    g_model = candidate;

    return env->NewStringUTF("MODEL LOADED");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_pocketai_app_MainActivity_nativeGenerate(
        JNIEnv* env, jobject, jstring jprompt) {
    if (!jprompt) {
        return env->NewStringUTF("ERROR: Empty prompt.");
    }

    const char* raw = env->GetStringUTFChars(jprompt, nullptr);
    if (!raw) {
        return env->NewStringUTF("ERROR: Could not read prompt.");
    }

    std::string user_text(raw);
    env->ReleaseStringUTFChars(jprompt, raw);

    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_model) {
        return env->NewStringUTF(
            "No model loaded. Select a GGUF model first.");
    }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = 2048;
    cp.n_batch = 128;
    cp.n_ubatch = 128;
    cp.n_threads = 4;
    cp.n_threads_batch = 4;

    llama_context* ctx = llama_init_from_model(g_model, cp);
    if (!ctx) {
        return env->NewStringUTF("ERROR: Could not create AI context.");
    }

    std::string prompt =
        "<|im_start|>system\n"
        "You are EDITH, a helpful offline AI assistant. "
        "Answer clearly and concisely.<|im_end|>\n"
        "<|im_start|>user\n" + user_text +
        "<|im_end|>\n<|im_start|>assistant\n";

    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    std::vector<llama_token> tokens(4096);

    int32_t count = llama_tokenize(
        vocab, prompt.c_str(),
        static_cast<int32_t>(prompt.size()),
        tokens.data(),
        static_cast<int32_t>(tokens.size()),
        true, false);

    if (count < 0) {
        tokens.resize(static_cast<size_t>(-count));
        count = llama_tokenize(
            vocab, prompt.c_str(),
            static_cast<int32_t>(prompt.size()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            true, false);
    }

    if (count <= 0 || count > 2048) {
        llama_free(ctx);
        return env->NewStringUTF(
            "ERROR: Prompt is empty or too long for this test.");
    }

    tokens.resize(static_cast<size_t>(count));

    int32_t offset = 0;
    while (offset < count) {
        int32_t amount = std::min(128, count - offset);
        llama_batch batch = llama_batch_get_one(
            tokens.data() + offset, amount);

        if (llama_decode(ctx, batch) != 0) {
            llama_free(ctx);
            return env->NewStringUTF(
                "ERROR: Prompt evaluation failed.");
        }
        offset += amount;
    }

    llama_sampler* sampler = llama_sampler_init_greedy();
    if (!sampler) {
        llama_free(ctx);
        return env->NewStringUTF("ERROR: Sampler initialization failed.");
    }

    std::string answer;
    char piece[256];

    for (int i = 0; i < 160; ++i) {
        llama_token token = llama_sampler_sample(sampler, ctx, -1);

        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }

        llama_sampler_accept(sampler, token);

        int32_t n = llama_token_to_piece(
            vocab, token, piece, sizeof(piece), 0, false);

        if (n > 0) {
            answer.append(piece, static_cast<size_t>(n));
        }

        llama_batch batch = llama_batch_get_one(&token, 1);
        if (llama_decode(ctx, batch) != 0) {
            break;
        }
    }

    llama_sampler_free(sampler);
    llama_free(ctx);

    if (answer.empty()) {
        answer = "The model returned no text. Try another prompt.";
    }

    return env->NewStringUTF(answer.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pocketai_app_MainActivity_nativeUnloadModel(
        JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}
