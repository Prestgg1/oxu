// SPDX-License-Identifier: GPL-3.0-or-later
//
// JNI bridge between Oxu and bergamot-translator / CLD2.
//
// Strings cross the boundary as UTF-8 byte arrays rather than jstring:
// GetStringUTFChars/NewStringUTF use "modified UTF-8", which mangles characters
// outside the BMP (emoji, some CJK) and can abort the VM under CheckJNI.

#include <jni.h>

#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

#include "translator/parser.h"
#include "translator/response.h"
#include "translator/response_options.h"
#include "translator/service.h"

#include "compact_lang_det.h"

using namespace marian::bergamot;

namespace {

std::mutex g_mutex;
std::unique_ptr<BlockingService> g_service;
std::unordered_map<std::string, std::shared_ptr<TranslationModel>> g_models;

BlockingService &service() {
    if (!g_service) {
        BlockingService::Config config;
        config.cacheSize = 256;
        config.logger.level = "off";
        g_service = std::make_unique<BlockingService>(config);
    }
    return *g_service;
}

std::shared_ptr<TranslationModel> model(const std::string &key) {
    auto it = g_models.find(key);
    if (it == g_models.end()) throw std::runtime_error("Model not loaded: " + key);
    return it->second;
}

std::string toString(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    const char *chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

std::string toString(JNIEnv *env, jbyteArray bytes) {
    jsize len = env->GetArrayLength(bytes);
    std::string out(static_cast<size_t>(len), '\0');
    env->GetByteArrayRegion(bytes, 0, len, reinterpret_cast<jbyte *>(&out[0]));
    return out;
}

jbyteArray toBytes(JNIEnv *env, const std::string &s) {
    jbyteArray out = env->NewByteArray(static_cast<jsize>(s.size()));
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(s.size()),
                            reinterpret_cast<const jbyte *>(s.data()));
    return out;
}

std::vector<std::string> toStrings(JNIEnv *env, jobjectArray inputs) {
    jsize n = env->GetArrayLength(inputs);
    std::vector<std::string> out;
    out.reserve(static_cast<size_t>(n));
    for (jsize i = 0; i < n; i++) {
        auto item = static_cast<jbyteArray>(env->GetObjectArrayElement(inputs, i));
        out.emplace_back(toString(env, item));
        env->DeleteLocalRef(item);
    }
    return out;
}

jobjectArray toByteArrays(JNIEnv *env, const std::vector<Response> &responses) {
    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray out = env->NewObjectArray(static_cast<jsize>(responses.size()), byteArrayClass, nullptr);
    for (size_t i = 0; i < responses.size(); i++) {
        jbyteArray item = toBytes(env, responses[i].target.text);
        env->SetObjectArrayElement(out, static_cast<jsize>(i), item);
        env->DeleteLocalRef(item);
    }
    return out;
}

std::vector<ResponseOptions> plainOptions(size_t n) {
    ResponseOptions opts;
    opts.HTML = false;
    opts.qualityScores = false;
    opts.alignment = false;
    opts.sentenceMappings = false;
    return std::vector<ResponseOptions>(n, opts);
}

void throwJava(JNIEnv *env, const char *message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(cls, message);
}

}  // namespace

#define OXU_JNI(ret, name) \
    extern "C" __attribute__((visibility("default"))) JNIEXPORT ret JNICALL \
    Java_io_github_Prestgg_oxu_bergamot_Bergamot_##name

OXU_JNI(void, nativeLoad)(JNIEnv *env, jobject, jstring jkey, jstring jconfig) {
    try {
        std::string key = toString(env, jkey);
        std::string config = toString(env, jconfig);
        std::lock_guard<std::mutex> lock(g_mutex);
        if (g_models.count(key)) return;
        auto options = parseOptionsFromString(config, /*validate=*/true, /*pathsDir=*/"");
        g_models[key] = std::make_shared<TranslationModel>(options);
    } catch (const std::exception &e) {
        throwJava(env, e.what());
    }
}

OXU_JNI(jboolean, nativeIsLoaded)(JNIEnv *env, jobject, jstring jkey) {
    std::string key = toString(env, jkey);
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_models.count(key) ? JNI_TRUE : JNI_FALSE;
}

OXU_JNI(void, nativeUnload)(JNIEnv *env, jobject, jstring jkey) {
    std::string key = toString(env, jkey);
    std::lock_guard<std::mutex> lock(g_mutex);
    g_models.erase(key);
}

OXU_JNI(void, nativeUnloadAll)(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_models.clear();
    g_service.reset();
}

OXU_JNI(jobjectArray, nativeTranslate)(JNIEnv *env, jobject, jstring jkey, jobjectArray inputs) {
    try {
        std::string key = toString(env, jkey);
        std::vector<std::string> texts = toStrings(env, inputs);
        std::vector<Response> responses;
        {
            std::lock_guard<std::mutex> lock(g_mutex);
            auto m = model(key);
            auto opts = plainOptions(texts.size());
            responses = service().translateMultiple(m, std::move(texts), opts);
        }
        return toByteArrays(env, responses);
    } catch (const std::exception &e) {
        throwJava(env, e.what());
        return nullptr;
    }
}

OXU_JNI(jobjectArray, nativePivot)(JNIEnv *env, jobject, jstring jfirst, jstring jsecond,
                                   jobjectArray inputs) {
    try {
        std::string first = toString(env, jfirst);
        std::string second = toString(env, jsecond);
        std::vector<std::string> texts = toStrings(env, inputs);
        std::vector<Response> responses;
        {
            std::lock_guard<std::mutex> lock(g_mutex);
            auto a = model(first);
            auto b = model(second);
            auto opts = plainOptions(texts.size());
            responses = service().pivotMultiple(a, b, std::move(texts), opts);
        }
        return toByteArrays(env, responses);
    } catch (const std::exception &e) {
        throwJava(env, e.what());
        return nullptr;
    }
}

// Returns "<code>\t<reliable 0|1>\t<percent>" (code is an ISO 639-1 code, "un" if unknown).
OXU_JNI(jstring, nativeDetect)(JNIEnv *env, jobject, jbyteArray jtext) {
    std::string text = toString(env, jtext);
    CLD2::CLDHints hints = {nullptr, nullptr, 0, CLD2::UNKNOWN_LANGUAGE};
    CLD2::Language language3[3];
    int percent3[3];
    double scores3[3];
    int chunkBytes = 0;
    bool reliable = false;
    CLD2::ExtDetectLanguageSummary(text.data(), static_cast<int>(text.size()), /*is_plain_text=*/true,
                                   &hints, 0, language3, percent3, scores3, nullptr, &chunkBytes,
                                   &reliable);
    std::string out = std::string(CLD2::LanguageCode(language3[0])) + "\t" +
                      (reliable ? "1" : "0") + "\t" + std::to_string(percent3[0]);
    return env->NewStringUTF(out.c_str());
}
