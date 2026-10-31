package io.github.sspanak.tt9.ui.main;

import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.ime.TraditionalT9;
import io.github.sspanak.tt9.ime.modes.InputModeKind;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.ui.main.keys.SoftKey;
import io.github.sspanak.tt9.util.sys.DeviceInfo;
import io.github.sspanak.tt9.util.sys.SystemSettings;

class MainLayoutTray extends MainLayoutExtraPanel {
	protected int height;
	protected boolean isCommandPaletteShown = false;
	protected boolean isTextEditingPaletteShown = false;

	// Sidephone: the strip is only on screen while there is something in it. Until the first
	// suggestions arrive there is nothing, so it starts collapsed.
	private boolean hasSuggestions = false;
	protected boolean isSymbolGridShown = false;
	private boolean isFlashing = false;
	private static final long STATUS_FLASH_DURATION = 1200;
	@NonNull private final Handler flashHandler = new Handler(Looper.getMainLooper());


	MainLayoutTray(TraditionalT9 tt9) {
		super(tt9, R.layout.main_small);
	}


	int getHeight(boolean forceRecalculate) {
		if (height <= 0 || forceRecalculate) {
			Resources resources = tt9.getResources();
			height = getStatusBarHeight(resources, tt9.getSettings()) + getPanelHeight(resources);
		}

		return height;
	}


	protected int getPanelHeight(@NonNull Resources resources) {
		if (isCommandPaletteShown() || isTextEditingPaletteShown()) {
			return resources.getDimensionPixelSize(R.dimen.main_small_command_palette_height);
		} else if (isSymbolGridShown) {
			return resources.getDimensionPixelSize(R.dimen.main_small_symbol_grid_height);
		} else {
			return 0;
		}
	}


	protected int getStatusBarHeight(@NonNull Resources resources, @NonNull SettingsStore settings) {
		float textSize = resources.getDimension(R.dimen.status_bar_text_size);
		float padding = textSize * 0.45f;
		padding = padding < 1 ? 1 : padding;
		return Math.round((padding + textSize) * settings.getSuggestionFontScale());
	}


	protected void setSoftKeysVisibility() {
		if (view != null) {
			togglePanel(R.id.main_soft_keys, false);
		}
	}


	@Override
	void onSuggestionsChanged(boolean hasSuggestions) {
		this.hasSuggestions = hasSuggestions;
		applyCollapse();
	}


	@Override
	void setSymbolGridShown(boolean shown) {
		isSymbolGridShown = shown;
		getHeight(true);
		applyCollapse();
	}


	/**
	 * Sidephone: a collapsed strip gives no sign that the input mode changed, so bring it up for a
	 * moment with the status text ("[ ABC / en ]", "[ English ]") and let it collapse again.
	 */
	@Override
	void flashStatus() {
		isFlashing = true;
		applyCollapse();
		flashHandler.removeCallbacksAndMessages(null);
		flashHandler.postDelayed(() -> {
			isFlashing = false;
			applyCollapse();
		}, STATUS_FLASH_DURATION);
	}


	/**
	 * The bar the OS keeps painting under a collapsed strip cannot be made to vanish on the SP-01, so
	 * it is at least painted in the keyboard colour, like the suggestion list, instead of the
	 * blending-dependent default.
	 */
	@Override
	void applyNavigationBar() {
		SystemSettings.setNavigationBarBackground(tt9.getWindow().getWindow(), tt9.getSettings(), true);
	}


	/**
	 * While the SYM grid is open the strip takes its real measured height instead of the computed
	 * one, so the grid is never cut off by a mis-estimated status row.
	 */
	@Override
	boolean setKeyboardHeight(int height) {
		if (!isSymbolGridShown) {
			return super.setKeyboardHeight(height);
		}

		View keyboard = view != null ? view.findViewById(R.id.keyboard_container) : null;
		ViewGroup.LayoutParams params = keyboard != null ? keyboard.getLayoutParams() : null;
		if (params == null) {
			return false;
		}

		params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
		keyboard.setLayoutParams(params);
		return true;
	}


	/**
	 * Sidephone: outside Predictive mode there is no resting state for the strip. When nothing is
	 * being composed, no palette is open and the SYM grid is closed, every part of the view is hidden and the bottom padding
	 * (the navigation bar allowance) is dropped, so the input view measures to zero height and the
	 * app behind gets its whole screen back. The window itself stays, which avoids the IME restarts
	 * that showing and hiding it would cause on every word.
	 */
	protected boolean isCollapsed() {
		// In Predictive mode the strip is always up (it shows the mode when there is nothing to
		// suggest); only ABC mode and the number mode collapse it between words.
		if (InputModeKind.isPredictive(tt9.getInputMode())) {
			return false;
		}

		return !hasSuggestions && !isSymbolGridShown && !isFlashing && !isFnPanelVisible();
	}


	protected void applyCollapse() {
		if (view == null) {
			return;
		}

		final boolean collapsed = isCollapsed();
		for (int id : new int[]{R.id.keyboard_container, R.id.bumper_left, R.id.bumper_right}) {
			togglePanel(id, !collapsed);
		}

		// the status row (mode text, language chip) says nothing useful over the grid, so it makes way
		togglePanel(R.id.tray_wrapper, !isSymbolGridShown);

		setPadding();
		applyNavigationBar();

		// A view of zero height makes Android paint its own "navigation guard" (white on a light theme)
		// under the collapsed strip instead of our navigation bar colour. One pixel of our own, in the
		// keyboard colour, keeps the bar painted like the suggestion list.
		if (collapsed) {
			view.setMinimumHeight(1);
			view.setBackgroundColor(tt9.getSettings().getKeyboardBackground() | 0xFF000000);
		} else {
			view.setMinimumHeight(0);
			setBackgroundBlending();
		}
	}


	@Override
	public void setPadding() {
		if (view != null && isCollapsed()) {
			view.setPadding(view.getPaddingLeft(), 0, view.getPaddingRight(), 0);
		} else {
			super.setPadding();
		}
	}


	@Override
	protected void setPadding(@NonNull View v, @NonNull WindowInsets windowInsets) {
		super.setPadding(v, windowInsets);
		if (isCollapsed()) {
			v.setPadding(v.getPaddingLeft(), 0, v.getPaddingRight(), 0);
		}
	}


	void showCommandPalette() {
		super.showCommandPalette();
		isCommandPaletteShown = true;
		isTextEditingPaletteShown = false;
		togglePanel(R.id.main_command_keys, true);
		getHeight(true);
		renderKeys(false);
		applyCollapse();
	}


	void showKeyboard() {
		super.showKeyboard();
		togglePanel(R.id.main_command_keys, false);
		isCommandPaletteShown = false;
		isTextEditingPaletteShown = false;
		getHeight(true);
		renderKeys(false);
		applyCollapse();
	}


	@Override
	void showTextEditingPalette() {
		super.showTextEditingPalette();
		isCommandPaletteShown = false;
		isTextEditingPaletteShown = true;
		togglePanel(R.id.main_command_keys, true);
		getHeight(true);
		renderKeys(false);
		applyCollapse();
	}


	@Override
	boolean isCommandPaletteShown() {
		return isCommandPaletteShown;
	}


	@Override
	boolean isTextEditingPaletteShown() {
		return isTextEditingPaletteShown;
	}


	@NonNull
	@Override
	protected ArrayList<SoftKey> getKeys() {
		if (view != null && keys.isEmpty()) {
			keys.addAll(getKeysFromContainer(view.findViewById(R.id.main_command_keys)));
		}
		return keys;
	}


	@Override
	void render() {
		final boolean isPortrait = !DeviceInfo.isLandscapeOrientation(tt9);

		getView();
		setSoftKeysVisibility();
		setPadding();
		setWidth(tt9.getSettings().getWidthPercent(isPortrait, null), tt9.getSettings().getAlignment(null));
		setBackgroundBlending();
		enableClickHandlers();
		renderKeys(false);
		applyCollapse();
	}
}
