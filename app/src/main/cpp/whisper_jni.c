// JNI bridge between io.github.sspanak.tt9.ime.voice.WhisperLib and whisper.cpp.
//
// Deliberately tiny: load a model, transcribe one buffer of 16 kHz mono float samples, free it.
// All calls are made from a single worker thread on the Java side (see WhisperSpeechRecognizer),
// so there is no locking here. The one exception is abort(), which only flips an atomic flag.

#include <jni.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#include "whisper.h"

#define LOG_TAG "SideTypeWhisper"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static atomic_bool g_abort = false;

static bool should_abort(void *user_data) {
	(void) user_data;
	return atomic_load(&g_abort);
}

JNIEXPORT jlong JNICALL
Java_io_github_sspanak_tt9_ime_voice_WhisperLib_init(JNIEnv *env, jclass clazz, jstring model_path) {
	(void) clazz;
	const char *path = (*env)->GetStringUTFChars(env, model_path, NULL);
	if (path == NULL) {
		return 0;
	}

	struct whisper_context_params cparams = whisper_context_default_params();
	cparams.use_gpu = false;

	struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
	if (ctx == NULL) {
		LOGE("Failed to load model: %s", path);
	}

	(*env)->ReleaseStringUTFChars(env, model_path, path);
	return (jlong) (intptr_t) ctx;
}

JNIEXPORT void JNICALL
Java_io_github_sspanak_tt9_ime_voice_WhisperLib_free(JNIEnv *env, jclass clazz, jlong handle) {
	(void) env;
	(void) clazz;
	if (handle != 0) {
		whisper_free((struct whisper_context *) (intptr_t) handle);
	}
}

JNIEXPORT void JNICALL
Java_io_github_sspanak_tt9_ime_voice_WhisperLib_abort(JNIEnv *env, jclass clazz) {
	(void) env;
	(void) clazz;
	atomic_store(&g_abort, true);
}

// Returns the transcription as UTF-8 bytes, or NULL when it failed or was aborted. Bytes rather
// than a jstring because NewStringUTF() wants "modified UTF-8" and chokes on 4-byte sequences,
// which Whisper can emit.
JNIEXPORT jbyteArray JNICALL
Java_io_github_sspanak_tt9_ime_voice_WhisperLib_transcribe(
	JNIEnv *env, jclass clazz, jlong handle, jfloatArray samples, jstring language, jint threads, jint audio_ctx
) {
	(void) clazz;
	struct whisper_context *ctx = (struct whisper_context *) (intptr_t) handle;
	if (ctx == NULL || samples == NULL) {
		return NULL;
	}

	const char *lang = language != NULL ? (*env)->GetStringUTFChars(env, language, NULL) : NULL;
	const jsize n_samples = (*env)->GetArrayLength(env, samples);
	jfloat *pcm = (*env)->GetFloatArrayElements(env, samples, NULL);
	if (pcm == NULL) {
		if (lang != NULL) (*env)->ReleaseStringUTFChars(env, language, lang);
		return NULL;
	}

	struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
	params.n_threads = threads > 0 ? threads : 4;
	params.language = lang != NULL ? lang : "en";
	params.translate = false;
	params.no_context = true;
	params.no_timestamps = true;
	params.single_segment = true;
	params.print_special = false;
	params.print_progress = false;
	params.print_realtime = false;
	params.print_timestamps = false;
	params.suppress_blank = true;
	params.suppress_nst = true;
	params.audio_ctx = audio_ctx > 0 ? audio_ctx : 0;
	params.abort_callback = should_abort;
	params.abort_callback_user_data = NULL;

	atomic_store(&g_abort, false);
	const int status = whisper_full(ctx, params, pcm, n_samples);

	(*env)->ReleaseFloatArrayElements(env, samples, pcm, JNI_ABORT);
	if (lang != NULL) (*env)->ReleaseStringUTFChars(env, language, lang);

	if (status != 0 || atomic_load(&g_abort)) {
		LOGI("Transcription %s (status %d)", atomic_load(&g_abort) ? "aborted" : "failed", status);
		return NULL;
	}

	size_t total = 0;
	const int n_segments = whisper_full_n_segments(ctx);
	for (int i = 0; i < n_segments; i++) {
		const char *text = whisper_full_get_segment_text(ctx, i);
		if (text != NULL) total += strlen(text);
	}

	char *joined = (char *) malloc(total + 1);
	if (joined == NULL) {
		return NULL;
	}
	size_t offset = 0;
	for (int i = 0; i < n_segments; i++) {
		const char *text = whisper_full_get_segment_text(ctx, i);
		if (text != NULL) {
			const size_t len = strlen(text);
			memcpy(joined + offset, text, len);
			offset += len;
		}
	}

	jbyteArray result = (*env)->NewByteArray(env, (jsize) offset);
	if (result != NULL) {
		(*env)->SetByteArrayRegion(env, result, 0, (jsize) offset, (const jbyte *) joined);
	}
	free(joined);
	return result;
}
