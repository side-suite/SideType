package io.github.sspanak.tt9.preferences.screens.keypad;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.languages.GeometryRegistry;
import io.github.sspanak.tt9.preferences.custom.EnhancedDropDownPreference;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;

/**
 * SID-125 (D8): "Keypad type" — Auto / Compact QWERTY / T9 keypad. Auto (default) uses keycode
 * detection and is authoritative; picking a specific tile hard-pins that geometry and disables
 * detection. The pin is applied immediately here and re-applied on input start (TypingHandler).
 */
public class DropDownKeypadType extends EnhancedDropDownPreference {
	public static final String NAME = SettingsStore.KEYPAD_TYPE;

	public DropDownKeypadType(@NonNull Context context) { super(context); }
	public DropDownKeypadType(@NonNull Context context, @Nullable AttributeSet attrs) { super(context, attrs); }
	public DropDownKeypadType(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); }
	public DropDownKeypadType(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) { super(context, attrs, defStyleAttr, defStyleRes); }

	@Override
	public EnhancedDropDownPreference populate(@NonNull SettingsStore settings) {
		add(SettingsStore.KEYPAD_TYPE_AUTO, R.string.pref_keypad_type_auto);
		add(GeometryRegistry.COMPACT_QWERTY, R.string.pref_keypad_type_compact);
		add("numpad", R.string.pref_keypad_type_numpad);
		commitOptions();
		super.setValue(settings.getKeypadType());
		return this;
	}

	@Override
	protected String getName() {
		return NAME;
	}

	@Override
	protected boolean onChange(Preference preference, Object newKey) {
		// Apply the pick right away: a specific tile forces + persists that geometry; Auto just re-enables
		// detection (the next keystroke's keycode decides the tile).
		String value = newKey.toString();
		if (!SettingsStore.KEYPAD_TYPE_AUTO.equals(value)) {
			GeometryRegistry.setActive(value);
		}
		return true;
	}
}
