package io.github.sspanak.tt9.ime.voice;

import android.content.Context;
import android.speech.SpeechRecognizer;

import androidx.annotation.NonNull;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.preferences.settings.SettingsStatic;
import io.github.sspanak.tt9.util.sys.DeviceInfo;

public class VoiceInputError {
	public final static int ERROR_NOT_AVAILABLE = 101;
	public final static int ERROR_INVALID_LANGUAGE = 102;
	// 103 (ERROR_CANNOT_BIND_TO_VOICE_SERVICE) and 104 (ERROR_START_FAILURE) described failures of the
	// platform SpeechRecognizer, which SideType no longer uses. Not reassigned, to keep the numbering
	// honest against old logs. See docs/adr/0002.
	public final static int ERROR_NO_MODEL_FOR_LANGUAGE = 105;
	public final static int ERROR_MODEL_DOWNLOAD_FAILED = 106;
	public final static int ERROR_MODEL_LOAD_FAILED = 107;
	public final static int ERROR_MODEL_NOT_DOWNLOADED = 108;
	/** The bytes arrived but are not what the catalog pinned. Not a network failure — see SID-54. */
	public final static int ERROR_MODEL_CORRUPTED = 109;
	/** A download for this model is already running. Not a failure — saying so would be a lie. */
	public final static int ERROR_MODEL_DOWNLOAD_IN_PROGRESS = 110;
	/** The online service could not be reached or answered with an error, and there is no local model to fall back on. */
	public final static int ERROR_REMOTE_FAILED = 111;
	/** The online service rejected the API key. */
	public final static int ERROR_REMOTE_UNAUTHORIZED = 112;

	public final int code;
	public final String message;
	public final String debugMessage;


	public VoiceInputError(Context context, int errorCode) {
		code = errorCode;
		debugMessage = codeToDebugString(errorCode);
		message = codeToString(context, errorCode);
	}


	public boolean isNoPermission() {
		return code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS;
	}


	public boolean isIrrelevantToUser() {
		return
			code == SpeechRecognizer.ERROR_NO_MATCH
			|| code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
			|| code == SpeechRecognizer.ERROR_AUDIO;
	}


	/**
	 * Whisper was not trained on this language, so there is nothing to download and no fallback to
	 * offer. Distinct from {@link #isModelNotDownloaded()}, which is a temporary state.
	 */
	public boolean isNoModelForLanguage() {
		return code == ERROR_NO_MODEL_FOR_LANGUAGE;
	}


	/** The model is not on disk yet. Recoverable: offer the download. */
	public boolean isModelNotDownloaded() {
		return code == ERROR_MODEL_NOT_DOWNLOADED;
	}


	@NonNull
	@Override
	public String toString() {
		return message;
	}


	@NonNull
	private static String codeToString(Context context, int code) {
		return switch (code) {
			case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
				-> context.getString(R.string.voice_input_error_no_permissions);
			case ERROR_NOT_AVAILABLE
				-> context.getString(R.string.voice_input_error_not_available);
			case ERROR_NO_MODEL_FOR_LANGUAGE
				-> context.getString(R.string.voice_input_error_no_model_for_language);
			case ERROR_MODEL_NOT_DOWNLOADED
				-> context.getString(R.string.voice_input_error_model_not_downloaded);
			case ERROR_MODEL_DOWNLOAD_FAILED
				-> context.getString(R.string.voice_input_error_model_download_failed);
			case ERROR_MODEL_CORRUPTED
				-> context.getString(R.string.voice_input_error_model_corrupted);
			case ERROR_MODEL_DOWNLOAD_IN_PROGRESS
				-> context.getString(R.string.voice_input_error_model_download_in_progress);
			case ERROR_MODEL_LOAD_FAILED
				-> context.getString(R.string.voice_input_error_model_load_failed);
			case ERROR_REMOTE_FAILED
				-> context.getString(R.string.voice_input_error_remote_failed);
			case ERROR_REMOTE_UNAUTHORIZED
				-> context.getString(R.string.voice_input_error_remote_unauthorized);
			default -> context.getString(R.string.voice_input_error_generic);
		};
	}


	private static String codeToDebugString(int code) {
		String message = codeToDebugStringCommon(code);
		return message != null ? message : "Unknown voice input error code: " + code;
	}


	/**
	 * The SpeechRecognizer.ERROR_* codes survive purely as a numbering scheme:
	 * {@link WhisperSpeechRecognizer} reuses the ones that still describe something real (no mic
	 * permission, recognizer busy, nothing said). The network/server codes are gone with the cloud
	 * backend: recognition is entirely on-device.
	 */
	private static String codeToDebugStringCommon(int code) {
		return switch (code) {
			case SpeechRecognizer.ERROR_AUDIO -> "Audio capture error.";
			case SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error.";
			case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "No microphone permissions.";
			case SpeechRecognizer.ERROR_NO_MATCH -> "No match.";
			case SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice input is already running.";
			case SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected.";
			case ERROR_NOT_AVAILABLE -> "Voice input is not available.";
			case ERROR_INVALID_LANGUAGE -> "Invalid language for voice input.";
			case ERROR_NO_MODEL_FOR_LANGUAGE -> "Whisper does not support this language.";
			case ERROR_MODEL_NOT_DOWNLOADED -> "The voice model has not been downloaded.";
			case ERROR_MODEL_DOWNLOAD_FAILED -> "Voice model download failed.";
			case ERROR_MODEL_CORRUPTED -> "Voice model failed its size/SHA-256 check and was discarded.";
			case ERROR_MODEL_DOWNLOAD_IN_PROGRESS -> "A voice model download is already running.";
			case ERROR_REMOTE_FAILED -> "Online transcription failed and no local model is available.";
			case ERROR_REMOTE_UNAUTHORIZED -> "The online transcription service rejected the API key.";
			case ERROR_MODEL_LOAD_FAILED -> "Could not load the voice model or the native Whisper library.";
			default -> null;
		};
	}
}
