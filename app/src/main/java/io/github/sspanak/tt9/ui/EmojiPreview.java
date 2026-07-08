package io.github.sspanak.tt9.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.Nullable;

import io.github.sspanak.tt9.preferences.settings.SettingsStore;

/**
 * A minimal overlay shown while the SYM key is held, previewing which emoji each Compact QWERTY key
 * will type in the emoji layer. Each cell shows the bound emoji on top and the key's letters below,
 * so it doubles as a key-location guide. Key indices match Key.codeToNumber().
 */
public class EmojiPreview {
	private static final int[][] ROWS = {
		{2, 3, 4, 5, 6},   // Q E T U O
		{7, 8, 9, 10, 11}, // A D G J L
		{12, 13, 14, 15},  // Z C B M
	};
	private static final String[][] LABELS = {
		{"QW", "ER", "TY", "UI", "OP"},
		{"AS", "DF", "GH", "JK", "L"},
		{"ZX", "CV", "BN", "M"},
	};

	@Nullable private PopupWindow popup;

	public void show(@Nullable View anchor, @Nullable SettingsStore settings) {
		// Idempotent: the SYM (ALT) key auto-repeats while held, delivering repeated ACTION_DOWN
		// events. Rebuilding the popup on each one makes it flicker/close, so if it is already
		// visible, keep it as-is and ignore the repeat.
		if (popup != null && popup.isShowing()) {
			return;
		}

		hide();
		if (anchor == null || anchor.getWindowToken() == null || settings == null) {
			return;
		}

		Context context = anchor.getContext();
		LinearLayout grid = buildGrid(context, settings);

		popup = new PopupWindow(grid, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, false);
		popup.setTouchable(false);
		popup.setClippingEnabled(false);
		try {
			popup.showAtLocation(anchor, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, anchor.getHeight() + dp(context, 8));
		} catch (Exception e) {
			popup = null;
		}
	}

	public void hide() {
		if (popup != null) {
			popup.dismiss();
			popup = null;
		}
	}

	private LinearLayout buildGrid(Context context, SettingsStore settings) {
		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		int pad = dp(context, 7);
		root.setPadding(pad, pad, pad, pad);

		// Semi-transparent so the input field / suggestions behind it stay legible when the preview
		// floats over them.
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(0xC81E1E1E);
		bg.setCornerRadius(dp(context, 12));
		root.setBackground(bg);

		for (int r = 0; r < ROWS.length; r++) {
			LinearLayout row = new LinearLayout(context);
			row.setOrientation(LinearLayout.HORIZONTAL);
			for (int c = 0; c < ROWS[r].length; c++) {
				row.addView(buildCell(context, ROWS[r][c], LABELS[r][c], settings));
			}
			root.addView(row);
		}

		return root;
	}

	/** One key: bound emoji (or a dim placeholder) on top, the key's letters below. */
	private LinearLayout buildCell(Context context, int index, String label, SettingsStore settings) {
		LinearLayout cell = new LinearLayout(context);
		cell.setOrientation(LinearLayout.VERTICAL);
		cell.setGravity(Gravity.CENTER);
		int m = dp(context, 2);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 40), LinearLayout.LayoutParams.WRAP_CONTENT);
		lp.setMargins(m, m, m, m);
		cell.setLayoutParams(lp);

		String emoji = settings.getEmojiBind(index);
		boolean bound = emoji != null && !emoji.isEmpty();

		TextView top = new TextView(context);
		top.setGravity(Gravity.CENTER);
		top.setText(bound ? emoji : "·");
		top.setTextSize(bound ? 18 : 15);
		top.setTextColor(bound ? 0xFFFFFFFF : 0xFF666666);
		cell.addView(top);

		TextView bottom = new TextView(context);
		bottom.setGravity(Gravity.CENTER);
		bottom.setText(label);
		bottom.setTextSize(10);
		bottom.setTextColor(bound ? 0xFFBBBBBB : 0xFF888888);
		cell.addView(bottom);

		return cell;
	}

	private int dp(Context context, int value) {
		return Math.round(value * context.getResources().getDisplayMetrics().density);
	}
}
