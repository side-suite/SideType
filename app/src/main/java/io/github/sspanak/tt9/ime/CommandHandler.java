package io.github.sspanak.tt9.ime;

import android.view.KeyEvent;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.commands.CmdCommandPalette;
import io.github.sspanak.tt9.commands.Command;
import io.github.sspanak.tt9.commands.CommandCollection;
import io.github.sspanak.tt9.db.words.DictionaryLoader;
import io.github.sspanak.tt9.ime.helpers.Key;
import io.github.sspanak.tt9.ime.modes.InputMode;
import io.github.sspanak.tt9.ime.modes.InputModeKind;
import io.github.sspanak.tt9.languages.LanguageCollection;
import io.github.sspanak.tt9.languages.NaturalLanguage;
import io.github.sspanak.tt9.ui.UI;
import io.github.sspanak.tt9.ui.tray.SymbolGrid;
import io.github.sspanak.tt9.ui.tray.SymbolGridPages;
import io.github.sspanak.tt9.util.Ternary;

abstract public class CommandHandler extends TextEditingHandler {
	private final CmdCommandPalette cmdPalette = new CmdCommandPalette();

	// Sidephone SYM grid: closed → symbols → emojis → closed, one SYM tap per step.
	private static final int GRID_CLOSED = 0;
	private static final int GRID_SYMBOLS = 1;
	private static final int GRID_EMOJIS = 2;
	@Nullable private SymbolGrid symbolGrid = null;
	private int symbolGridMode = GRID_CLOSED;
	private int symbolGridPage = 0;
	@androidx.annotation.NonNull private List<String> symbolGridItems = new ArrayList<>();


	@Override
	protected Ternary onBack() {
		if (isSymbolGridShown()) {
			hideSymbolGrid();
			return Ternary.TRUE;
		} else if (cmdPalette.hideCommandPalette(getFinalContext())) {
			return Ternary.TRUE;
		} else {
			return super.onBack();
		}
	}


	@Override
	protected void initTray() {
		super.initTray();
		symbolGrid = new SymbolGrid(mainView.getView(), settings, this::pickSymbolGridCell);
		symbolGridMode = GRID_CLOSED; // a new view starts with the grid hidden
	}


	/******** Sidephone SYM grid ********/

	/** SYM tap: closed → symbols → emojis → closed. */
	public void toggleSymbolGrid() {
		switch (symbolGridMode) {
			case GRID_CLOSED -> openSymbolGrid(GRID_SYMBOLS);
			case GRID_SYMBOLS -> openSymbolGrid(GRID_EMOJIS);
			default -> hideSymbolGrid();
		}
	}


	public void showSymbolGrid(boolean emojis) {
		openSymbolGrid(emojis ? GRID_EMOJIS : GRID_SYMBOLS);
	}


	private void openSymbolGrid(int mode) {
		if (shouldBeOff() || symbolGrid == null || !symbolGrid.isAvailable()) {
			return;
		}

		if (symbolGridMode == GRID_CLOSED) {
			// the half-typed word is finished first, so the symbol lands after it, not inside it
			suggestionOps.cancelDelayedAccept();
			mInputMode.onAcceptSuggestion(suggestionOps.acceptIncomplete());
			if (mainView.isCommandPaletteShown() || mainView.isTextEditingPaletteShown()) {
				mainView.showKeyboard();
			}
		}

		symbolGridMode = mode;
		symbolGridPage = 0;
		symbolGridItems = mode == GRID_EMOJIS ? SymbolGridPages.emojis(this, settings) : SymbolGridPages.symbols(settings, mLanguage);
		renderSymbolGrid();
	}


	private void renderSymbolGrid() {
		if (symbolGrid == null) {
			return;
		}

		final int pages = SymbolGridPages.pageCount(symbolGridItems);
		symbolGridPage = ((symbolGridPage % pages) + pages) % pages;
		symbolGrid.show(SymbolGridPages.page(symbolGridItems, symbolGridPage), (symbolGridPage + 1) + "/" + pages);
		mainView.setSymbolGridShown(true);
		forceShowWindow();
	}


	@Override
	protected void hideSymbolGrid() {
		if (symbolGridMode == GRID_CLOSED) {
			return;
		}

		symbolGridMode = GRID_CLOSED;
		symbolGridItems = new ArrayList<>();
		if (symbolGrid != null) {
			symbolGrid.hide();
		}
		mainView.setSymbolGridShown(false);
	}


	@Override
	protected boolean isSymbolGridShown() {
		return symbolGridMode != GRID_CLOSED;
	}


	/**
	 * Key dispatch while the grid is open. The 14 letter keys pick their cell, the ▲▼ key turns the
	 * page, and Space / Enter just close the grid. Anything else (Backspace, Shift, the navigation
	 * row) is left alone and works as usual.
	 */
	@Override
	protected boolean onSymbolGridKey(int keyCode) {
		if (Key.isCompactQwertyLetter(keyCode)) {
			pickSymbolGridCell(Key.codeToNumber(settings, keyCode) - 2);
			return true;
		}

		if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == settings.getKeyNextSuggestion()) {
			symbolGridPage++;
			renderSymbolGrid();
			return true;
		}

		if (keyCode == KeyEvent.KEYCODE_SPACE || Key.isOK(keyCode)) {
			hideSymbolGrid();
			return true;
		}

		// Backspace is not intercepted: it deletes as usual and the grid stays open

		return false;
	}


	private void pickSymbolGridCell(int cell) {
		final int index = symbolGridPage * SymbolGridPages.PAGE_SIZE + cell;
		if (!isSymbolGridShown() || cell < 0 || cell >= SymbolGridPages.PAGE_SIZE || index >= symbolGridItems.size()) {
			return;
		}

		final String glyph = symbolGridItems.get(index);
		if (glyph.isEmpty()) {
			return; // a filler cell on the "recent" page
		}
		if (symbolGridMode == GRID_EMOJIS) {
			settings.addRecentEmoji(glyph); // first page next time
		}
		if (!settings.getSymbolGridKeepOpen()) {
			hideSymbolGrid();
		}
		onText(glyph, false);
	}


	@Override
	protected boolean onNumber(int key, boolean hold, int repeat) {
		if (statusBar.isErrorShown()) {
			resetStatus();
		}

		if (!shouldBeOff() && mainView.isCommandPaletteShown()) {
			Command cmd = CommandCollection.getByHardKey(CommandCollection.COLLECTION_PALETTE, key);
			if (cmd.isAvailable(getFinalContext())) {
				cmd.run(getFinalContext());
			}
			return true;
		}

		return super.onNumber(key, hold, repeat);
	}


	@Override
	protected boolean navigateBack() {
		return cmdPalette.hideCommandPalette(getFinalContext()) || super.navigateBack();
	}


	public void resetStatus() {
		if (mainView.isCommandPaletteShown()) {
			statusBar.setText(R.string.commands_select_command);
			statusBar.setAccessibilityText(R.string.commands_select_command);
		} else if (mainView.isTextEditingPaletteShown()) {
			statusBar.setText(R.string.commands_select_command);
			statusBar.setAccessibilityText(R.string.commands_select_command);
		} else {
			statusBar.setText(mInputMode);
			statusBar.setAccessibilityText(mInputMode);
		}
	}


	public void setInputMode(int modeId) {
		if (!allowedInputModes.contains(modeId) && modeId != InputMode.MODE_RECOMPOSING) {
			return;
		}

		suggestionOps.cancelDelayedAccept();
		mInputMode.onAcceptSuggestion(suggestionOps.acceptIncomplete());
		mInputMode.onFinishWord(); // a word typed in ABC mode is complete once the user leaves ABC mode
		resetKeyRepeat();

		mInputMode = InputMode.getInstance(settings, mLanguage, inputType, textField, modeId);
		determineTextCase();

		if (modeId != InputMode.MODE_RECOMPOSING) {
			settings.saveInputMode(mInputMode.getId());
		}

		// update the UI
		getDisplayTextCase(mLanguage, mInputMode.getTextCase());
		setStatusIcon(mInputMode, mLanguage);
		statusBar.setText(mInputMode);
		statusBar.setAccessibilityText(mInputMode);
		mainView.render();
		mainView.flashStatus(); // Sidephone: show the new mode even if the strip is collapsed

		if (settings.isMainLayoutStealth() && !settings.isStatusIconEnabled()) {
			UI.toastShortSingle(this, mInputMode.getClass().getSimpleName(), mInputMode.toString());
		}
	}


	public void setLang(int langId) {
		if (!mEnabledLanguages.contains(langId)) {
			return;
		}

		suggestionOps.cancelDelayedAccept();
		stopVoiceInput();

		mLanguage = LanguageCollection.getLanguage(langId);
		validateLanguages();

		settings.setDefaultChars(mLanguage, false); // initialize default order, if missing
		((NaturalLanguage) mLanguage).updateKeyCharacters(settings); // and update the layout for 2..9 keys, if needed

		// for languages that do not have ABC or Predictive, make sure we remain in valid state
		mInputMode = InputMode
			.getInstance(settings, mLanguage, inputType, textField, determineInputModeId())
			.copy(mInputMode);

		if (mInputMode.isTyping()) {
			getSuggestions(0, null, this::onAfterLanguageChange);
		} else {
			onAfterLanguageChange();
		}

		if (InputModeKind.isPredictive(mInputMode)) {
			DictionaryLoader.autoLoad(this, settings, mLanguage);
		}

		mindReader.setLanguage(mLanguage).seed(getFinalContext(), mLanguage);

		forceShowWindow();
	}


	private void onAfterLanguageChange() {
		getDisplayTextCase(mLanguage, mInputMode.getTextCase());
		setStatusIcon(mInputMode, mLanguage);
		statusBar.setText(mInputMode);
		statusBar.setAccessibilityText(mInputMode);
		suggestionOps.setLanguage(mLanguage);
		mainView.render();
		if (settings.isMainLayoutStealth() && !settings.isStatusIconEnabled()) {
			UI.toastShortSingle(this, mInputMode.getClass().getSimpleName(), mInputMode.toString());
		}
	}


	public boolean nextTextCase() {
		final String currentWord = !suggestionOps.isEmpty() && mInputMode.isTyping() ? suggestionOps.getCurrent() : "";

		if (!mInputMode.nextTextCase(currentWord, displayTextCase)) {
			return false;
		}

		mInputMode.skipNextTextCaseDetection();
		if (!InputModeKind.isRecomposing(mInputMode)) {
			settings.saveTextCase(mInputMode.getTextCase());
		}

		if (currentWord.isEmpty() && !suggestionOps.isEmpty()) {
			// if we have set the suggestions from a different source, e.g. Clipboard or MindReader,
			// they won't be in the InputMode's state, so adjust the list directly, without any specific rules
			suggestionOps.setTextCase(mLanguage, mInputMode.getTextCase());
			appHacks.setComposingText(suggestionOps.getCurrent());
			return true;
		} else if (currentWord.isEmpty() || (currentWord.length() == 1 && !Character.isAlphabetic(currentWord.charAt(0)))) {
			// if there are no suggestions, or they are special chars, we don't need to adjust their text case
			return true;
		}

		// if there are suggestions, we need to adjust their text case to acknowledge the change
		int currentSuggestionIndex = suggestionOps.getCurrentIndex();
		currentSuggestionIndex = suggestionOps.containsStem() ? currentSuggestionIndex - 1 : currentSuggestionIndex;

		suggestionOps.set(mInputMode.getSuggestions(), currentSuggestionIndex, mInputMode.containsGeneratedSuggestions());

		if (InputModeKind.isRecomposing(mInputMode)) {
			appHacks.setComposingTextPartsWithHighlightedJoining(mInputMode.getWordStem() + suggestionOps.getCurrent(), mInputMode.getRecomposingSuffix());
		} else {
			mindReader.setTextCase(mInputMode.getTextCaseRaw());
			suggestionOps.addGuesses(mindReader.getGuesses());
			appHacks.setComposingText(suggestionOps.getCurrent());
		}

		return true;
	}
}
