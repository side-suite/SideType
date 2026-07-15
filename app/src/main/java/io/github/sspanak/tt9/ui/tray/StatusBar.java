package io.github.sspanak.tt9.ui.tray;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.ime.modes.InputMode;
import io.github.sspanak.tt9.ime.voice.VoiceInputOps;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.ui.main.ResizableMainView;
import io.github.sspanak.tt9.ui.notifications.DictionaryLoadingBar;
import io.github.sspanak.tt9.util.Logger;

public class StatusBar {
	private boolean isShown = true;
	private double lastClickTime = 0;

	@NonNull private final ResizableMainView mainView;
	@Nullable private final TextView statusView;
	@NonNull private final SettingsStore settings;
	@Nullable private String statusText;

	@NonNull private final DictionaryLoadingBar loadingBar;
	@NonNull private final Runnable onLoadingFinished;
	@NonNull private final Runnable onSwipe;


	public StatusBar(@NonNull Context context, @NonNull SettingsStore settings, @NonNull ResizableMainView mainView, @NonNull Runnable onDictionaryLoadingFinished, @NonNull Runnable onSwipe) {
		this.mainView = mainView;
		this.settings = settings;
		statusView = mainView.getView() != null ? mainView.getView().findViewById(R.id.status_bar) : null;
		if (statusView != null) {
			statusView.setOnTouchListener(this::onTouch);
		}

		loadingBar = DictionaryLoadingBar.getInstance(context);
		loadingBar.setOnStatusChange2(this::onLoading);
		onLoadingFinished = onDictionaryLoadingFinished;
		this.onSwipe = onSwipe;
	}


	/**
	 * Handle double-click and drag resizing
	 */
	private boolean onTouch(View v, MotionEvent event) {
		final int action = event.getActionMasked();

		if (!isShown) {
			if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_DOWN) {
				onSwipe.run();
			}
			return false;
		}

		switch (action) {
			case MotionEvent.ACTION_DOWN:
				mainView.onResizeStart(event.getRawY());
				return true;
			case MotionEvent.ACTION_MOVE:
				if (settings.getDragResize()) {
					mainView.onResizeThrottled(event.getRawY());
				}
				return true;
			case MotionEvent.ACTION_UP:
				long now = System.currentTimeMillis();
				if (settings.getDoubleTapResize() && now - lastClickTime < SettingsStore.SOFT_KEY_DOUBLE_CLICK_DELAY) {
					mainView.onSnap();
				} else if (settings.getDragResize()) {
					mainView.onResize(event.getRawY());
				}

				lastClickTime = now;

				return true;
		}

		return false;
	}


	public boolean isErrorShown() {
		return statusText != null && statusText.startsWith("❌");
	}


	public StatusBar setColorScheme() {
		final HostTrayTheme host = HostTrayTheme.getInstance();
		final int foreground = host.barForeground(settings.getKeyboardTextColor());

		if (statusView != null) {
			// SID-17: status text shares the tray foreground channel so it stays readable on a host tint.
			statusView.setTextColor(foreground);
		}

		// SID-17: when a trusted host supplies a tray background, tint the whole strip (quick-action icons
		// + status/suggestion rows) so the host color reads as one coherent surface. With no host override
		// this clears back to transparent, letting the strip show SideType's normal keyboard background.
		// The strip's container differs by layout — tray_wrapper on the SP-01 Tray/Small strip, or the
		// stock status_bar_container on the Classic/Numpad top bars — so tint whichever the layout has.
		final View trayContainer = findTrayContainer();
		if (trayContainer != null) {
			trayContainer.setBackgroundColor(host.barBackground(Color.TRANSPARENT));
		}

		setQuickActionColors(foreground);
		setTopSeparatorColor(host);

		return this;
	}


	/**
	 * The Sidephone quick actions (settings / add-word / language / emoji) declare
	 * {@code ?attr/colorKeyboardText} in XML, which resolves once at inflation from the static theme.
	 * Without this they stay at the theme color and read as white islands on a host tint, so re-apply
	 * the tray foreground here on every color-scheme pass. Absent from the Classic/Numpad layouts —
	 * hence the null checks. See SID-17.
	 */
	private void setQuickActionColors(int foreground) {
		final View root = mainView.getView();
		if (root == null) {
			return;
		}

		for (int id : new int[]{R.id.sidephone_settings_key, R.id.sidephone_dict_key, R.id.sidephone_emoji_key}) {
			final ImageView icon = root.findViewById(id);
			if (icon != null) {
				icon.setImageTintList(ColorStateList.valueOf(foreground));
			}
		}

		final TextView languageKey = root.findViewById(R.id.sidephone_language_key);
		if (languageKey != null) {
			languageKey.setTextColor(foreground);
		}
	}


	/**
	 * The 1dp rule above the tray divides the keyboard from the app behind it. Under a host tint that
	 * default-colored line reads as a hard seam across the host's surface, so blend it into the tray
	 * background — the same reasoning that extends the tint onto the navigation bar below. With no host
	 * override this resolves back to the static separator color. See SID-17.
	 */
	private void setTopSeparatorColor(@NonNull HostTrayTheme host) {
		final View root = mainView.getView();
		if (root == null) {
			return;
		}

		final View separator = root.findViewById(R.id.keyboard_top_separator);
		if (separator != null) {
			separator.setBackgroundColor(host.barBackground(ContextCompat.getColor(separator.getContext(), R.color.keyboard_top_separator)));
		}
	}


	/**
	 * SID-17: the whole-tray container to tint, resolved per layout — {@code tray_wrapper} on the SP-01
	 * Tray/Small strip, or the stock {@code status_bar_container} on the Classic/Numpad top bars.
	 */
	@Nullable
	private View findTrayContainer() {
		final View root = mainView.getView();
		if (root == null) {
			return null;
		}

		final View wrapper = root.findViewById(R.id.tray_wrapper);
		return wrapper != null ? wrapper : root.findViewById(R.id.status_bar_container);
	}


	public void setAccessibilityText(int stringResourceId) {
		if (statusView != null && stringResourceId != 0) {
			setAccessibilityText(statusView.getContext().getString(stringResourceId));
		}
	}


	public void setAccessibilityText(@Nullable String text) {
		if (statusView != null && text != null) {
			statusView.announceForAccessibility(text);
		}
	}


	public void setAccessibilityText(@NonNull InputMode inputMode) {
		if (statusView != null) {
			setAccessibilityText(inputMode.toAccessibilityString(statusView.getContext()));
		}
	}


	public void setAccessibilityTextCase(@NonNull InputMode inputMode) {
		if (statusView == null) {
			return;
		}

		String accessibilityText = switch (inputMode.getTextCase()) {
			case InputMode.CASE_LOWER -> statusView.getContext().getString(R.string.accessibility_text_case_lower);
			case InputMode.CASE_UPPER -> statusView.getContext().getString(R.string.accessibility_text_case_upper);
			case InputMode.CASE_CAPITALIZE -> statusView.getContext().getString(R.string.accessibility_text_case_capital);
			default -> null;
		};

		setAccessibilityText(accessibilityText);
	}


	public void setError(String error) {
		setAccessibilityText(error);
		setText("❌  " + error);
	}


	public void setText(int stringResourceId) {
		if (statusView != null && stringResourceId != 0) {
			setText(statusView.getContext().getString(stringResourceId));
		}
	}


	public void setText(String text) {
		statusText = text;
		this.render();
	}


	public void setText(InputMode inputMode) {
		setText("[ " + inputMode.toString() + " ]");
	}


	public void setText(VoiceInputOps voiceInputOps) {
		setText("[ " + voiceInputOps.toString() + " ]");
	}


	public void setShown(boolean yes) {
		if (isShown != yes) {
			isShown = yes;
			render();
		}
	}


	private void onLoading() {
		if (loadingBar.inProgress()) {
			setText("[ " + loadingBar.getShortMessage() + " ]");
		} else if (loadingBar.isCancelled() || loadingBar.isFailed()) {
			setError(loadingBar.getShortMessage());
		} else {
			onLoadingFinished.run();
		}
	}


	private void render() {
		if (statusView == null) {
			return;
		}

		if (statusText == null) {
			Logger.w("StatusBar.render", "Not displaying NULL status");
			return;
		}

		if (!isShown) {
			statusView.setText(null);
			return;
		}

		SpannableString scaledText = new SpannableString(statusText);
		scaledText.setSpan(new RelativeSizeSpan(settings.getSuggestionFontScale()), 0, statusText.length(), 0);

		statusView.setText(scaledText);
	}
}
