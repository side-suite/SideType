package io.github.sspanak.tt9.ui.tray;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;

/**
 * Sidephone: the on-screen 5×3 grid that mirrors the physical key tile. Each cell shows one symbol
 * or emoji above the letters of the key that types it, so the grid doubles as a map of the tile.
 * The bottom-right cell is dead (that key is Backspace) and shows the page counter instead.
 * <p>
 * Cells are numbered 0–13 in reading order, which is the key index minus 2: Q=0 … O=4, A=5 … L=9,
 * Z=10 … M=13. Tapping a cell picks it the same way pressing its key does.
 */
public class SymbolGrid {
	public static final int COLUMNS = 5;
	public static final int ROWS = 3;
	public static final int CELLS = COLUMNS * ROWS - 1; // the bottom-right key is Backspace

	private static final String[] KEY_LABELS = {
		"QW", "ER", "TY", "UI", "OP",
		"AS", "DF", "GH", "JK", "L",
		"ZX", "CV", "BN", "M",
	};

	public interface OnPick { void pick(int cell); }

	@Nullable private final LinearLayout container;
	private final TextView[] glyphViews = new TextView[CELLS];
	@Nullable private TextView pageView;


	public SymbolGrid(@Nullable View root, @NonNull SettingsStore settings, @NonNull OnPick onPick) {
		container = root != null ? root.findViewById(R.id.main_symbol_grid) : null;
		if (container == null) {
			return;
		}

		container.removeAllViews();
		final Context context = container.getContext();
		final int textColor = settings.getKeyboardTextColor();
		// extra room below the last row, so the OS bar painted under the strip does not sit on it
		final int pad = dp(context, 4);
		container.setPadding(pad, pad, pad, dp(context, 16));

		for (int row = 0; row < ROWS; row++) {
			LinearLayout rowView = new LinearLayout(context);
			rowView.setOrientation(LinearLayout.HORIZONTAL);
			for (int col = 0; col < COLUMNS; col++) {
				final int cell = row * COLUMNS + col;
				rowView.addView(cell < CELLS ? buildCell(context, cell, textColor, onPick) : buildDeadCell(context, textColor));
			}
			container.addView(rowView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		}
	}


	public boolean isAvailable() {
		return container != null;
	}


	public void show(@NonNull List<String> items, @NonNull String pageLabel) {
		if (container == null) {
			return;
		}

		for (int i = 0; i < CELLS; i++) {
			final boolean filled = i < items.size() && !items.get(i).isEmpty();
			glyphViews[i].setText(filled ? items.get(i) : "");
			((View) glyphViews[i].getParent()).setAlpha(filled ? 1f : 0.3f);
		}

		if (pageView != null) {
			pageView.setText(pageLabel);
		}

		container.setVisibility(View.VISIBLE);
	}


	public void hide() {
		if (container != null) {
			container.setVisibility(View.GONE);
		}
	}


	private LinearLayout buildCell(@NonNull Context context, int cell, int textColor, @NonNull OnPick onPick) {
		LinearLayout cellView = newCell(context);

		GradientDrawable bg = new GradientDrawable();
		bg.setColor((textColor & 0x00FFFFFF) | 0x16000000);
		bg.setCornerRadius(dp(context, 6));
		cellView.setBackground(bg);
		cellView.setClickable(true);
		cellView.setOnClickListener(v -> onPick.pick(cell));

		TextView glyph = new TextView(context);
		glyph.setGravity(Gravity.CENTER);
		glyph.setTextSize(20);
		glyph.setTextColor(textColor);
		glyph.setSingleLine(true);
		cellView.addView(glyph);
		glyphViews[cell] = glyph;

		TextView label = new TextView(context);
		label.setGravity(Gravity.CENTER);
		label.setText(KEY_LABELS[cell]);
		label.setTextSize(9);
		label.setTextColor(textColor);
		label.setAlpha(0.55f);
		cellView.addView(label);

		return cellView;
	}


	/** The Backspace position: nothing to pick, so it carries the page counter. */
	private LinearLayout buildDeadCell(@NonNull Context context, int textColor) {
		LinearLayout cellView = newCell(context);

		pageView = new TextView(context);
		pageView.setGravity(Gravity.CENTER);
		pageView.setTextSize(11);
		pageView.setTextColor(textColor);
		pageView.setAlpha(0.55f);
		cellView.addView(pageView);

		return cellView;
	}


	private LinearLayout newCell(@NonNull Context context) {
		LinearLayout cellView = new LinearLayout(context);
		cellView.setOrientation(LinearLayout.VERTICAL);
		cellView.setGravity(Gravity.CENTER);

		final int margin = dp(context, 2);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(context, 44), 1f);
		params.setMargins(margin, margin, margin, margin);
		cellView.setLayoutParams(params);

		return cellView;
	}


	private static int dp(@NonNull Context context, int value) {
		return Math.round(value * context.getResources().getDisplayMetrics().density);
	}
}
