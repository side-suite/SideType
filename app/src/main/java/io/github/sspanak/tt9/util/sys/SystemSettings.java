package io.github.sspanak.tt9.util.sys;

import android.content.Context;
import android.os.LocaleList;
import android.provider.Settings;
import android.view.Window;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Locale;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.ui.tray.HostTrayTheme;


public class SystemSettings {
	private static InputMethodManager inputManager;
	private static Integer originalNavigationBarColor = null;
	private static String packageName;


	public static boolean isNightModeOn(@NonNull Context context) {
		return context.getString(R.string.system_night_mode).equals("y");
	}


	public static boolean isTT9Enabled(@Nullable Context context) {
		if (context == null) {
			return false;
		}

		inputManager = inputManager == null ? (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE) : inputManager;
		packageName = packageName == null ? context.getPackageName() : packageName;

		for (final InputMethodInfo imeInfo : inputManager.getEnabledInputMethodList()) {
			if (packageName.equals(imeInfo.getPackageName())) {
				return true;
			}
		}
		return false;
	}

	public static boolean isTT9Selected(@NonNull Context context) {
		String defaultIME = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
		inputManager = inputManager == null ? (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE) : inputManager;
		packageName = packageName == null ? context.getPackageName() : packageName;

		for (final InputMethodInfo imeInfo : inputManager.getEnabledInputMethodList()) {
			if (packageName.equals(imeInfo.getPackageName()) && imeInfo.getId().equals(defaultIME)) {
				return true;
			}
		}
		return false;
	}

	@NonNull
	public static String getLocale() {
		Locale locale = LocaleList.getDefault().get(0);
		String country = locale.getCountry();
		String language = locale.getLanguage();

		if (language.equals(Locale.ENGLISH.getLanguage())) {
			country = "";
		}

		return country.isEmpty() ? language : language + "_" + country;
	}

	@Nullable
	public static String getPreviousIME(Context context) {
		inputManager = inputManager == null ? (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE) : inputManager;
		packageName = packageName == null ? context.getPackageName() : packageName;

		for (final InputMethodInfo imeInfo : inputManager.getEnabledInputMethodList()) {
			if (!packageName.equals(imeInfo.getPackageName())) {
				return imeInfo.getId();
			}
		}

		return null;
	}

	public static void setNavigationBarBackground(@Nullable Window window, @NonNull SettingsStore settings, boolean enableBlending) {
		if (!DeviceInfo.AT_LEAST_ANDROID_11 || window == null) {
			return;
		}

		window.setNavigationBarContrastEnforced(!enableBlending);

		if (enableBlending) {
			// SID-17: the nav bar tracks the effective tray background — a trusted host's barBg when
			// present, else the keyboard background — for both its color and its light/dark icon contrast,
			// so a light host tint still gets readable (dark) nav-bar icons.
			final int navBarColor = HostTrayTheme.getInstance().barBackground(settings.getKeyboardBackground());
			final boolean navBarDark = ColorUtils.calculateLuminance(navBarColor) < 0.5;

			// See: <a href="https://stackoverflow.com/a/77240330">the only working solution for the insets</a>.
			WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(window, window.getDecorView());
			insetsController.setAppearanceLightNavigationBars(!navBarDark);

			if (DeviceInfo.AT_LEAST_ANDROID_12 && !DeviceInfo.AT_LEAST_ANDROID_15) { // Android 12-14
				originalNavigationBarColor = originalNavigationBarColor == null ? window.getNavigationBarColor() : originalNavigationBarColor;
				// Extend the host tint onto the OS navigation bar the IME paints, so the strip below the
				// tray doesn't stay a jarring default-colored seam. (No-op on Android 15+, where
				// setNavigationBarColor is deprecated and the bar is system-driven.)
				window.setNavigationBarColor(navBarColor);
			}
		} else if (originalNavigationBarColor != null) {
			window.setNavigationBarColor(originalNavigationBarColor); // Android 12-14
			originalNavigationBarColor = null;
		}
	}
}
