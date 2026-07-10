package io.github.sspanak.tt9.preferences;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.ui.EdgeToEdgeActivity;

/**
 * Visual keypad grid for assigning an emoji to each Compact QWERTY key. The bound emoji is typed by
 * holding SYM and pressing the key (see KeyPadHandler / SettingsKeyChars.getEmojiBind). The key
 * indices here must match Key.codeToNumber().
 */
public class EmojiBindsActivity extends EdgeToEdgeActivity {
	// {display label, key index} laid out like the physical tile (3 rows).
	private static final Object[][] KEYS = {
		{"Q W", 2}, {"E R", 3}, {"T Y", 4}, {"U I", 5}, {"O P", 6},
		{"A S", 7}, {"D F", 8}, {"G H", 9}, {"J K", 10}, {"L", 11},
		{"Z X", 12}, {"C V", 13}, {"B N", 14}, {"M", 15},
	};
	private static final int COLUMNS = 5;

	private SettingsStore settings;
	private final ArrayList<Button> keyButtons = new ArrayList<>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		settings = new SettingsStore(this);
		setTitle(R.string.pref_emoji_binds);
		setContentView(buildLayout());
	}

	@Override
	protected void onResume() {
		super.onResume();
		refreshKeys();
	}

	private View buildLayout() {
		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		int pad = dp(16);
		root.setPadding(pad, pad, pad, pad);

		TextView hint = new TextView(this);
		hint.setText(R.string.emoji_binds_hint);
		hint.setPadding(0, 0, 0, dp(16));
		root.addView(hint);

		GridLayout grid = new GridLayout(this);
		grid.setColumnCount(COLUMNS);
		keyButtons.clear();
		for (Object[] key : KEYS) {
			final int index = (int) key[1];
			Button b = new Button(this);
			b.setAllCaps(false);
			b.setMinWidth(0);
			b.setMinimumWidth(0);
			b.setOnClickListener(v -> showEmojiPicker(index));
			GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
			lp.width = 0;
			lp.height = dp(64);
			lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
			lp.setMargins(dp(2), dp(2), dp(2), dp(2));
			grid.addView(b, lp);
			b.setTag(key[0]);
			keyButtons.add(b);
		}
		root.addView(grid);

		ScrollView scroll = new ScrollView(this);
		scroll.addView(root);
		return scroll;
	}

	private void refreshKeys() {
		for (Button b : keyButtons) {
			int index = keyIndexOf(b);
			String bind = settings.getEmojiBind(index);
			String label = String.valueOf(b.getTag());
			b.setText(label + "\n" + (bind.isEmpty() ? "—" : bind));
		}
	}

	private int keyIndexOf(Button b) {
		String label = String.valueOf(b.getTag());
		for (Object[] key : KEYS) {
			if (key[0].equals(label)) {
				return (int) key[1];
			}
		}
		return -1;
	}

	private void showEmojiPicker(int keyIndex) {
		final int columns = 6;
		int gridPad = dp(8);
		// Size each cell so exactly `columns` fit inside the dialog width (dialogs are inset from the
		// screen edges), otherwise the right-most column clips off screen.
		int available = getResources().getDisplayMetrics().widthPixels - dp(80) - gridPad * 2;
		final int cellSize = Math.max(dp(40), available / columns);

		LinearLayout container = new LinearLayout(this);
		container.setOrientation(LinearLayout.VERTICAL);

		// Emoji | Symbols mode toggle: you can bind either a picked emoji or a symbol to a key.
		LinearLayout modeRow = new LinearLayout(this);
		modeRow.setOrientation(LinearLayout.HORIZONTAL);
		final TextView emojiMode = new TextView(this);
		final TextView symbolMode = new TextView(this);
		for (TextView t : new TextView[]{emojiMode, symbolMode}) {
			t.setTextSize(15);
			t.setGravity(Gravity.CENTER);
			t.setPadding(dp(14), dp(8), dp(14), dp(8));
		}
		emojiMode.setText(R.string.emoji_binds_mode_emoji);
		symbolMode.setText(R.string.emoji_binds_mode_symbols);
		modeRow.addView(emojiMode);
		modeRow.addView(symbolMode);
		container.addView(modeRow);

		// Category tab strip (emoji mode only).
		LinearLayout tabRow = new LinearLayout(this);
		tabRow.setOrientation(LinearLayout.HORIZONTAL);
		HorizontalScrollView tabScroll = new HorizontalScrollView(this);
		tabScroll.setHorizontalScrollBarEnabled(false);
		tabScroll.addView(tabRow);
		container.addView(tabScroll);

		GridLayout grid = new GridLayout(this);
		grid.setColumnCount(columns);
		grid.setPadding(gridPad, gridPad, gridPad, gridPad);
		ScrollView gridScroll = new ScrollView(this);
		gridScroll.addView(grid);
		container.addView(gridScroll);

		final ArrayList<TextView> tabs = new ArrayList<>();
		for (int i = 0; i < io.github.sspanak.tt9.ui.EmojiData.CATEGORIES.length; i++) {
			final int catIndex = i;
			TextView tab = new TextView(this);
			tab.setText((String) io.github.sspanak.tt9.ui.EmojiData.CATEGORIES[i][0]);
			tab.setTextSize(20);
			tab.setGravity(Gravity.CENTER);
			tab.setPadding(dp(10), dp(6), dp(10), dp(6));
			tab.setOnClickListener(v -> {
				populateGrid(grid, catIndex, cellSize, keyIndex);
				highlightTab(tabs, catIndex);
				gridScroll.scrollTo(0, 0);
			});
			tabs.add(tab);
			tabRow.addView(tab);
		}

		emojiMode.setOnClickListener(v -> {
			tabScroll.setVisibility(View.VISIBLE);
			emojiMode.setAlpha(1f);
			symbolMode.setAlpha(0.5f);
			populateGrid(grid, 0, cellSize, keyIndex);
			highlightTab(tabs, 0);
			gridScroll.scrollTo(0, 0);
		});
		symbolMode.setOnClickListener(v -> {
			tabScroll.setVisibility(View.GONE);
			emojiMode.setAlpha(0.5f);
			symbolMode.setAlpha(1f);
			populateSymbolGrid(grid, cellSize, keyIndex);
			gridScroll.scrollTo(0, 0);
		});

		// Default mode: Emoji.
		emojiMode.setAlpha(1f);
		symbolMode.setAlpha(0.5f);
		populateGrid(grid, 0, cellSize, keyIndex);
		highlightTab(tabs, 0);

		currentDialog = new AlertDialog.Builder(this)
			.setTitle(R.string.emoji_binds_pick)
			.setView(container)
			.setNeutralButton(R.string.emoji_binds_clear, (d, w) -> {
				settings.setEmojiBind(keyIndex, "");
				refreshKeys();
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}

	private void populateGrid(GridLayout grid, int catIndex, int cellSize, int keyIndex) {
		grid.removeAllViews();
		for (String emoji : io.github.sspanak.tt9.ui.EmojiData.getEmojiForCategory(catIndex)) {
			grid.addView(makeBindCell(emoji, 22, cellSize, keyIndex));
		}
	}

	private void populateSymbolGrid(GridLayout grid, int cellSize, int keyIndex) {
		grid.removeAllViews();
		for (String symbol : io.github.sspanak.tt9.ui.EmojiData.SYMBOLS) {
			grid.addView(makeBindCell(symbol, 20, cellSize, keyIndex));
		}
	}

	private TextView makeBindCell(String glyph, int textSize, int cellSize, int keyIndex) {
		TextView cell = new TextView(this);
		cell.setText(glyph);
		cell.setTextSize(textSize);
		cell.setGravity(Gravity.CENTER);
		cell.setOnClickListener(v -> {
			settings.setEmojiBind(keyIndex, glyph);
			refreshKeys();
			if (currentDialog != null) currentDialog.dismiss();
		});
		GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
		lp.width = cellSize;
		lp.height = cellSize;
		cell.setLayoutParams(lp);
		return cell;
	}

	private void highlightTab(ArrayList<TextView> tabs, int activeIndex) {
		for (int i = 0; i < tabs.size(); i++) {
			TextView tab = tabs.get(i);
			if (i == activeIndex) {
				GradientDrawable bg = new GradientDrawable();
				bg.setColor(0x33888888);
				bg.setCornerRadius(dp(8));
				tab.setBackground(bg);
			} else {
				tab.setBackground(null);
			}
		}
	}

	private AlertDialog currentDialog;

	private int dp(int value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
	}
}
