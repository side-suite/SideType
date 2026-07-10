package io.github.sspanak.tt9.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowInsets;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.ArrayList;

import io.github.sspanak.tt9.preferences.settings.SettingsStore;

/**
 * On-screen Emoji | Symbols drawer (Sidephone). Opened from the status-bar emoji button, it lets you
 * insert an emoji or symbol that is awkward to reach on the 2-letter tile. Tapping a glyph types it
 * straight into the focused field and keeps the drawer open, so several can be picked in a row.
 *
 * It is shown as a touchable-but-NOT-focusable PopupWindow anchored over the keyboard: the target
 * text field keeps input focus, so the type-callback can insert into it. Because the drawer covers
 * most of a small screen, a preview bar along the top mirrors the text around the cursor so you can
 * see what you are typing. A ⚙ corner opens the key-binds editor.
 */
public class EmojiDrawer {
	public interface OnPick { void pick(String glyph); }
	/** Supplies the text around the cursor for the preview bar (before + │ marker + after). */
	public interface PreviewSupplier { String text(); }

	private static final int EMOJI_COLUMNS = 8;
	private static final int SYMBOL_COLUMNS = 8;

	@Nullable private PopupWindow popup;

	@Nullable private GridLayout grid;
	@Nullable private ScrollView gridScroll;
	@Nullable private TextView previewView;
	@Nullable private PreviewSupplier previewSupplier;
	private int cellSize;

	public boolean isShowing() {
		return popup != null && popup.isShowing();
	}

	public void show(@Nullable View anchor, @Nullable SettingsStore settings, @Nullable OnPick onPick, @Nullable Runnable openBinds, @Nullable PreviewSupplier preview) {
		hide();
		if (anchor == null || anchor.getWindowToken() == null || settings == null || onPick == null) {
			return;
		}

		Context context = anchor.getContext();
		int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
		int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
		int width = anchor.getWidth() > 0 ? anchor.getWidth() : screenWidth;
		cellSize = Math.max(dp(context, 40), width / EMOJI_COLUMNS);
		previewSupplier = preview;

		LinearLayout root = buildRoot(context, onPick, openBinds);

		// Keep the drawer to at most ~half the screen so some of the real field stays visible above it,
		// and never taller than a comfortable fixed cap.
		int height = Math.min(dp(context, 250), Math.round(screenHeight * 0.5f));

		// Lift the drawer above the system navigation bar so its bottom row is not swallowed by the
		// gesture pill / nav buttons that sit at the very bottom of the screen.
		int navBarInset = getNavBarInset(anchor);

		popup = new PopupWindow(root, width, height, false);
		popup.setTouchable(true);
		popup.setClippingEnabled(false);
		try {
			popup.showAtLocation(anchor, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, navBarInset);
		} catch (Exception e) {
			popup = null;
			return;
		}
		updatePreview();
	}

	public void hide() {
		if (popup != null) {
			try {
				popup.dismiss();
			} catch (Exception ignored) {
				// dismissing a popup whose window is already gone is harmless
			}
			popup = null;
		}
		grid = null;
		gridScroll = null;
		previewView = null;
		previewSupplier = null;
	}

	private int getNavBarInset(View anchor) {
		try {
			WindowInsets insets = anchor.getRootWindowInsets();
			if (insets != null) {
				return insets.getSystemWindowInsetBottom();
			}
		} catch (Exception ignored) {
			// no insets available (very old devices) — sit flush with the bottom
		}
		return 0;
	}

	private LinearLayout buildRoot(Context context, OnPick onPick, @Nullable Runnable openBinds) {
		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackground(new ColorDrawable(0xFF202020));

		// --- Preview bar: mirrors the text around the cursor so you can see what you type. ---
		previewView = new TextView(context);
		previewView.setSingleLine(true);
		previewView.setEllipsize(TextUtils.TruncateAt.START); // keep the cursor end visible
		previewView.setTextColor(0xFFEEEEEE);
		previewView.setTextSize(15);
		previewView.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
		previewView.setBackground(new ColorDrawable(0xFF2C2C2C));
		root.addView(previewView);

		// --- Top bar: [ Emoji ] [ Symbols ] .... [ ⚙ ] [ ✕ ] ---
		LinearLayout topBar = new LinearLayout(context);
		topBar.setOrientation(LinearLayout.HORIZONTAL);
		topBar.setGravity(Gravity.CENTER_VERTICAL);

		TextView emojiTab = makeTab(context, "Emoji");
		TextView symbolTab = makeTab(context, "Symbols");
		topBar.addView(emojiTab);
		topBar.addView(symbolTab);

		View spacer = new View(context);
		topBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

		if (openBinds != null) {
			TextView gear = makeIconButton(context, "⚙");
			gear.setContentDescription("Edit key binds");
			gear.setOnClickListener(v -> { hide(); openBinds.run(); });
			topBar.addView(gear);
		}

		TextView close = makeIconButton(context, "✕");
		close.setContentDescription("Close");
		close.setOnClickListener(v -> hide());
		topBar.addView(close);

		root.addView(topBar);

		// --- Category strip (emoji mode only) ---
		HorizontalScrollView catScroll = new HorizontalScrollView(context);
		catScroll.setHorizontalScrollBarEnabled(false);
		LinearLayout catRow = new LinearLayout(context);
		catRow.setOrientation(LinearLayout.HORIZONTAL);
		catScroll.addView(catRow);
		root.addView(catScroll);

		// --- Glyph grid ---
		grid = new GridLayout(context);
		grid.setColumnCount(EMOJI_COLUMNS);
		int gp = dp(context, 4);
		grid.setPadding(gp, gp, gp, gp);
		gridScroll = new ScrollView(context);
		gridScroll.addView(grid);
		root.addView(gridScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

		final ArrayList<TextView> catTabs = new ArrayList<>();
		for (int i = 0; i < EmojiData.CATEGORIES.length; i++) {
			final int catIndex = i;
			TextView tab = new TextView(context);
			tab.setText((String) EmojiData.CATEGORIES[i][0]);
			tab.setTextSize(20);
			tab.setGravity(Gravity.CENTER);
			tab.setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6));
			tab.setOnClickListener(v -> {
				populateEmoji(context, catIndex, onPick);
				highlight(catTabs, catIndex);
			});
			catTabs.add(tab);
			catRow.addView(tab);
		}

		// Tab switching between Emoji and Symbols modes.
		emojiTab.setOnClickListener(v -> {
			catScroll.setVisibility(View.VISIBLE);
			setActiveTab(emojiTab, symbolTab, true);
			populateEmoji(context, 0, onPick);
			highlight(catTabs, 0);
		});
		symbolTab.setOnClickListener(v -> {
			catScroll.setVisibility(View.GONE);
			setActiveTab(emojiTab, symbolTab, false);
			populateSymbols(context, onPick);
		});

		// Default view: Emoji, first category.
		setActiveTab(emojiTab, symbolTab, true);
		populateEmoji(context, 0, onPick);
		highlight(catTabs, 0);

		return root;
	}

	private void updatePreview() {
		if (previewView == null) {
			return;
		}
		String text = previewSupplier != null ? previewSupplier.text() : "";
		previewView.setText(text == null ? "" : text);
	}

	private void populateEmoji(Context context, int catIndex, OnPick onPick) {
		if (grid == null) {
			return;
		}
		grid.setColumnCount(EMOJI_COLUMNS);
		grid.removeAllViews();
		for (String emoji : EmojiData.getEmojiForCategory(catIndex)) {
			grid.addView(makeCell(context, emoji, 22, onPick));
		}
		if (gridScroll != null) {
			gridScroll.scrollTo(0, 0);
		}
	}

	private void populateSymbols(Context context, OnPick onPick) {
		if (grid == null) {
			return;
		}
		grid.setColumnCount(SYMBOL_COLUMNS);
		grid.removeAllViews();
		for (String symbol : EmojiData.SYMBOLS) {
			grid.addView(makeCell(context, symbol, 20, onPick));
		}
		if (gridScroll != null) {
			gridScroll.scrollTo(0, 0);
		}
	}

	private TextView makeCell(Context context, String glyph, int textSize, OnPick onPick) {
		TextView cell = new TextView(context);
		cell.setText(glyph);
		cell.setTextSize(textSize);
		cell.setTextColor(0xFFFFFFFF);
		cell.setGravity(Gravity.CENTER);
		cell.setClickable(true);
		cell.setBackground(pressableBackground());
		cell.setOnClickListener(v -> {
			flash(v);
			try {
				v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
			} catch (Exception ignored) {
				// haptics are optional
			}
			onPick.pick(glyph);
			updatePreview();
		});
		GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
		lp.width = cellSize;
		lp.height = cellSize;
		cell.setLayoutParams(lp);
		return cell;
	}

	/** Brief highlight on tap, so a pick gives visible feedback even though the popup isn't focusable. */
	private void flash(View v) {
		v.setBackgroundColor(0x55FFFFFF);
		v.postDelayed(() -> v.setBackground(pressableBackground()), 120);
	}

	/** Transparent by default, translucent while pressed. */
	private StateListDrawable pressableBackground() {
		StateListDrawable bg = new StateListDrawable();
		bg.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(0x55FFFFFF));
		bg.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
		return bg;
	}

	private TextView makeTab(Context context, String label) {
		TextView tab = new TextView(context);
		tab.setText(label);
		tab.setTextSize(15);
		tab.setTextColor(0xFFFFFFFF);
		tab.setGravity(Gravity.CENTER);
		tab.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
		return tab;
	}

	private TextView makeIconButton(Context context, String glyph) {
		TextView b = new TextView(context);
		b.setText(glyph);
		b.setTextSize(18);
		b.setTextColor(0xFFDDDDDD);
		b.setGravity(Gravity.CENTER);
		b.setClickable(true);
		b.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
		b.setBackground(pressableBackground());
		return b;
	}

	private void setActiveTab(TextView emojiTab, TextView symbolTab, boolean emojiActive) {
		emojiTab.setAlpha(emojiActive ? 1f : 0.5f);
		symbolTab.setAlpha(emojiActive ? 0.5f : 1f);
	}

	private void highlight(ArrayList<TextView> tabs, int activeIndex) {
		for (int i = 0; i < tabs.size(); i++) {
			TextView tab = tabs.get(i);
			if (i == activeIndex) {
				GradientDrawable bg = new GradientDrawable();
				bg.setColor(0x33FFFFFF);
				bg.setCornerRadius(dp(tab.getContext(), 8));
				tab.setBackground(bg);
			} else {
				tab.setBackground(null);
			}
		}
	}

	private int dp(Context context, int value) {
		return Math.round(value * context.getResources().getDisplayMetrics().density);
	}
}
