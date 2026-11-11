package io.github.sspanak.tt9.ime.voice;

/**
 * The JNI surface of whisper.cpp (src/main/cpp/whisper_jni.c). Everything except {@link #abort()}
 * must be called from the single engine thread in {@link WhisperSpeechRecognizer}: a Whisper context
 * is not safe to use from two threads at once.
 * <p>
 * Loading the class loads the native library, so the first touch can throw
 * {@link UnsatisfiedLinkError}. Callers catch it and report a model-load failure.
 */
final class WhisperLib {
	static {
		System.loadLibrary("sidetype_whisper");
	}

	private WhisperLib() {}

	/** Loads a ggml model file. Returns a handle, or 0 when the file could not be loaded. */
	static native long init(String modelPath);

	static native void free(long handle);

	/**
	 * Transcribes 16 kHz mono samples in [-1, 1]. Returns UTF-8 bytes, or null when the run failed
	 * or was aborted. {@code audioCtx} shortens the encoder's window (0 = the model's full 30 s).
	 */
	static native byte[] transcribe(long handle, float[] samples, String language, int threads, int audioCtx);

	/** Asks a running {@link #transcribe} to give up. Safe from any thread. */
	static native void abort();
}
