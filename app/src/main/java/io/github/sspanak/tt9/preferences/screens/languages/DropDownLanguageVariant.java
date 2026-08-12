package io.github.sspanak.tt9.preferences.screens.languages;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import java.util.Locale;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.db.words.DictionaryLoader;
import io.github.sspanak.tt9.languages.GeometryRegistry;
import io.github.sspanak.tt9.languages.LanguageCollection;
import io.github.sspanak.tt9.languages.LanguageDefinition;
import io.github.sspanak.tt9.languages.NaturalLanguage;
import io.github.sspanak.tt9.preferences.custom.EnhancedDropDownPreference;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;

/**
 * SID-124: "keyboard layout" variant picker for a language that declares more than one Compact QWERTY
 * variant (today only German: QWERTY / QWERTZ). It hides itself when no enabled language has a choice to
 * make. The pick is stored per (geometry, language) in {@link GeometryRegistry}, not under this preference's
 * own key — a single static key cannot encode which language it targets — and the new (geometry, variant)
 * combo materializes lazily on next use.
 */
public class DropDownLanguageVariant extends EnhancedDropDownPreference {
	public static final String NAME = "pref_language_variant";

	private int targetLanguageId = -1;
	private SettingsStore settings;

	public DropDownLanguageVariant(@NonNull Context context) { super(context); }
	public DropDownLanguageVariant(@NonNull Context context, @Nullable AttributeSet attrs) { super(context, attrs); }
	public DropDownLanguageVariant(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); }
	public DropDownLanguageVariant(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) { super(context, attrs, defStyleAttr, defStyleRes); }

	@Override
	protected String getName() {
		return NAME;
	}

	@Override
	public EnhancedDropDownPreference populate(@NonNull SettingsStore settings) {
		this.settings = settings;
		// The variant lives in GeometryRegistry keyed by language, so this preference must not persist to its
		// own single key (it cannot say which language it means).
		setPersistent(false);

		NaturalLanguage language = findVariantCapableLanguage(settings);
		if (language == null) {
			setVisible(false);
			return this;
		}

		targetLanguageId = language.getId();
		setVisible(true);
		setTitle(getContext().getString(R.string.pref_language_variant, language.getName()));

		values.clear();
		for (String variant : language.getVariants(GeometryRegistry.COMPACT_QWERTY)) {
			add(variant, variantLabel(variant));
		}
		commitOptions();
		super.setValue(GeometryRegistry.getVariant(GeometryRegistry.COMPACT_QWERTY, targetLanguageId));
		return this;
	}

	@Override
	protected boolean onChange(Preference preference, Object newKey) {
		if (targetLanguageId < 0) {
			return true;
		}
		// Persist the pick; the (de, compact-qwerty, <variant>) tables are encoded on-device on next use — the
		// DB suffix changes and DictionaryLoader fills the new tables. See SID-124.
		GeometryRegistry.setVariant(GeometryRegistry.COMPACT_QWERTY, targetLanguageId, newKey.toString());

		// SID-135 (C1): don't wait for the first keystroke — if the compact tile is the active geometry (so the
		// just-picked variant IS the active combo), kick the encode now, with the cancellable progress bar.
		// loadIfEmpty is a no-op when the combo is already materialized (e.g. switching back to the pre-encoded
		// default), so this costs nothing on the common path. Skipped off the compact tile, where the variant
		// is not yet usable anyway.
		if (settings != null && GeometryRegistry.COMPACT_QWERTY.equals(GeometryRegistry.getActive().getId())) {
			DictionaryLoader.loadIfEmpty(getContext(), settings, LanguageCollection.getLanguage(targetLanguageId));
		}
		return true;
	}

	/** The first enabled language that declares more than one Compact QWERTY variant, or null if none does. */
	@Nullable
	private NaturalLanguage findVariantCapableLanguage(@NonNull SettingsStore settings) {
		for (int langId : settings.getEnabledLanguageIds()) {
			NaturalLanguage language = LanguageCollection.getLanguage(langId);
			if (language != null && language.getVariants(GeometryRegistry.COMPACT_QWERTY).size() > 1) {
				return language;
			}
		}
		return null;
	}

	/** The default variant reads "QWERTY" (the tile's keycaps); any other variant reads as its upper-cased name. */
	private String variantLabel(@NonNull String variant) {
		return LanguageDefinition.DEFAULT_VARIANT.equals(variant)
			? getContext().getString(R.string.pref_language_variant_default)
			: variant.toUpperCase(Locale.ROOT);
	}
}
