package io.github.sspanak.tt9.preferences;

import android.graphics.Paint;
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

		// Category tab strip.
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
		for (int i = 0; i < CATEGORIES.length; i++) {
			final int catIndex = i;
			TextView tab = new TextView(this);
			tab.setText((String) CATEGORIES[i][0]);
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
		for (String emoji : getEmojiForCategory(catIndex)) {
			TextView cell = new TextView(this);
			cell.setText(emoji);
			cell.setTextSize(22);
			cell.setGravity(Gravity.CENTER);
			cell.setOnClickListener(v -> {
				settings.setEmojiBind(keyIndex, emoji);
				refreshKeys();
				if (currentDialog != null) currentDialog.dismiss();
			});
			GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
			lp.width = cellSize;
			lp.height = cellSize;
			grid.addView(cell, lp);
		}
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

	// Emoji categories, each a representative icon (tab label) + the Unicode code-point ranges that
	// mostly fall under it. Android exposes no categorized emoji API, so the ranges are curated here.
	// Ranges are disjoint to avoid duplicates; hasGlyph() filters out anything the device can't draw.
	private static final Object[][] CATEGORIES = {
		// smileys (faces only)
		{"😀", new int[][]{{0x1F600, 0x1F644}, {0x1F910, 0x1F917}, {0x1F920, 0x1F92F}, {0x1F970, 0x1F97A}, {0x1F9D0, 0x1F9D0}}},
		// people, hands & gestures — thumbs up/down (1F44D/E), 🙏🙌 (1F64C/F), 🤝🤙 (1F918-1F91F), ✊✋✌ (270A-270D)
		{"👍", new int[][]{{0x1F440, 0x1F450}, {0x1F466, 0x1F487}, {0x1F595, 0x1F596}, {0x1F645, 0x1F64F}, {0x1F918, 0x1F91F}, {0x1F930, 0x1F93A}, {0x1F9B5, 0x1F9BB}, {0x1F9D1, 0x1F9DD}, {0x261D, 0x261D}, {0x270A, 0x270D}}},
		// animals & nature
		{"🐻", new int[][]{{0x1F400, 0x1F43F}, {0x1F980, 0x1F9AE}, {0x1F330, 0x1F344}}},
		// food & drink
		{"🍔", new int[][]{{0x1F345, 0x1F37F}, {0x1F32D, 0x1F32F}, {0x1F950, 0x1F96F}, {0x1F9C0, 0x1F9CB}}},
		// activity & sport
		{"⚽", new int[][]{{0x1F380, 0x1F3D3}, {0x1F3C0, 0x1F3C9}, {0x1F93C, 0x1F93F}, {0x26BD, 0x26BE}}},
		// travel & places
		{"🚗", new int[][]{{0x1F680, 0x1F6FF}, {0x1F3E0, 0x1F3F0}, {0x1F30D, 0x1F320}, {0x1F5FB, 0x1F5FF}}},
		// objects & clothing
		{"👕", new int[][]{{0x1F451, 0x1F465}, {0x1F4A0, 0x1F4FF}, {0x1F510, 0x1F5FA}, {0x1F9F0, 0x1F9FF}}},
		// symbols (dingbats/weather; hands 270A-270D excluded — they live under People)
		{"❤", new int[][]{{0x2600, 0x2638}, {0x263A, 0x26FF}, {0x2700, 0x2709}, {0x270E, 0x27BF}, {0x1F500, 0x1F50F}}},
	};

	private ArrayList<String> getEmojiForCategory(int catIndex) {
		// Enumerate the category's code point ranges and keep the ones the device's font can draw.
		Paint paint = new Paint();
		ArrayList<String> all = new ArrayList<>();
		int[][] ranges = (int[][]) CATEGORIES[catIndex][1];
		for (int[] range : ranges) {
			for (int cp = range[0]; cp <= range[1]; cp++) {
				String emoji = new String(Character.toChars(cp));
				if (paint.hasGlyph(emoji)) {
					all.add(emoji);
				}
			}
		}
		return all;
	}

	private int dp(int value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
	}
}
