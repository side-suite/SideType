package io.github.sspanak.tt9.ime.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import io.github.sspanak.tt9.preferences.settings.SettingsStore;

/**
 * Sends a finished recording to an online Whisper service and returns the text.
 * <p>
 * Speaks the OpenAI "audio transcriptions" API, which other hosts (Groq, self-hosted servers)
 * also implement, so the endpoint and the model name are settings rather than constants. It is
 * only ever created when the user has entered an API key; without one, nothing leaves the phone.
 */
public class RemoteWhisperClient {
	public static final String DEFAULT_URL = "https://api.openai.com/v1/audio/transcriptions";
	public static final String DEFAULT_MODEL = "whisper-1";

	private static final int CONNECT_TIMEOUT_MS = 8000;
	private static final int READ_TIMEOUT_MS = 30000;
	private static final String BOUNDARY = "----SideTypeVoice7d93b1a2c4e5";

	/** The service refused the API key. Not worth retrying or hiding behind the local model. */
	static class UnauthorizedException extends IOException {
		UnauthorizedException(String message) { super(message); }
	}

	@NonNull private final String url;
	@NonNull private final String apiKey;
	@NonNull private final String model;
	@Nullable private volatile HttpURLConnection connection;

	private RemoteWhisperClient(@NonNull String url, @NonNull String apiKey, @NonNull String model) {
		this.url = url;
		this.apiKey = apiKey;
		this.model = model;
	}

	/** A client for the service in the settings, or null when no API key has been entered. */
	@Nullable
	public static RemoteWhisperClient fromSettings(@NonNull SettingsStore settings) {
		final String key = settings.getVoiceApiKey();
		return key.isEmpty() ? null : new RemoteWhisperClient(settings.getVoiceApiUrl(), key, settings.getVoiceApiModel());
	}

	/** Uploads 16 kHz mono 16-bit samples and returns the transcription. Blocks; call off the main thread. */
	@NonNull
	String transcribe(@NonNull short[] pcm, int sampleCount, @NonNull String languageCode) throws IOException {
		final byte[] head = buildHead(languageCode);
		final byte[] wav = toWav(pcm, sampleCount);
		final byte[] tail = ("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8);

		final HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
		connection = http;
		try {
			http.setConnectTimeout(CONNECT_TIMEOUT_MS);
			http.setReadTimeout(READ_TIMEOUT_MS);
			http.setRequestMethod("POST");
			http.setDoOutput(true);
			http.setRequestProperty("Authorization", "Bearer " + apiKey);
			http.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
			http.setRequestProperty("Accept", "application/json");
			http.setFixedLengthStreamingMode(head.length + wav.length + tail.length);

			try (OutputStream out = http.getOutputStream()) {
				out.write(head);
				out.write(wav);
				out.write(tail);
			}

			final int status = http.getResponseCode();
			if (status == 401 || status == 403) {
				throw new UnauthorizedException("HTTP " + status);
			}
			if (status < 200 || status >= 300) {
				throw new IOException("HTTP " + status + ": " + readAll(http.getErrorStream(), 300));
			}

			final String body = readAll(http.getInputStream(), 1_000_000);
			try {
				return new JSONObject(body).optString("text", "");
			} catch (Exception e) {
				throw new IOException("Unexpected response from the transcription service");
			}
		} finally {
			connection = null;
			http.disconnect();
		}
	}

	/** Drops a request in flight. Safe from any thread; the blocked transcribe() then throws. */
	void abort() {
		final HttpURLConnection http = connection;
		if (http != null) {
			new Thread(http::disconnect, "voice-upload-abort").start();
		}
	}

	@NonNull
	private byte[] buildHead(@NonNull String languageCode) {
		final StringBuilder head = new StringBuilder();
		appendField(head, "model", model);
		appendField(head, "response_format", "json");
		// The API takes two-letter ISO-639-1 codes. The few longer Whisper codes are left out, and
		// the service detects the language by itself.
		if (languageCode.length() == 2) {
			appendField(head, "language", languageCode);
		}
		head.append("--").append(BOUNDARY).append("\r\n")
			.append("Content-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n")
			.append("Content-Type: audio/wav\r\n\r\n");
		return head.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static void appendField(@NonNull StringBuilder out, @NonNull String name, @NonNull String value) {
		out.append("--").append(BOUNDARY).append("\r\n")
			.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
			.append(value).append("\r\n");
	}

	/** Wraps the samples in a minimal 44-byte WAV header. */
	@NonNull
	private static byte[] toWav(@NonNull short[] pcm, int sampleCount) {
		final int dataSize = sampleCount * 2;
		final ByteBuffer wav = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
		wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataSize).put("WAVE".getBytes(StandardCharsets.US_ASCII));
		wav.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1);
		wav.putInt(WhisperModelCatalog.SAMPLE_RATE).putInt(WhisperModelCatalog.SAMPLE_RATE * 2).putShort((short) 2).putShort((short) 16);
		wav.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataSize);
		for (int i = 0; i < sampleCount; i++) {
			wav.putShort(pcm[i]);
		}
		return wav.array();
	}

	@NonNull
	private static String readAll(@Nullable InputStream in, int limit) throws IOException {
		if (in == null) {
			return "";
		}
		try (InputStream stream = in) {
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			final byte[] buffer = new byte[4096];
			int read;
			while (out.size() < limit && (read = stream.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		}
	}
}
