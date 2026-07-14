/**
 * generate-en-gb-dictionary.js
 *
 * Produces app/languages/dictionaries/en-GB-utf8.csv as a razor-thin re-ranking
 * of the existing en-utf8.csv: every British spelling variant is bumped just
 * above its American counterpart, and every other line is byte-identical to the
 * English source. American spellings are left untouched and remain as
 * lower-ranked fallbacks. No words are added, removed, or rewritten.
 *
 * The American<->British pairing comes from VarCon (Kevin Atkinson's variant
 * dataset, vendored at scripts/varcon/varcon.txt). VarCon supplies the pairing
 * only; the spellings already exist in en-utf8.csv. A pair is applied only when
 * BOTH members are present in en-utf8.csv.
 *
 * This is a dev-time tool. It is NOT wired into the Gradle build. Re-running it
 * reproduces en-GB-utf8.csv byte-for-byte from the two committed inputs.
 *
 * Usage: node scripts/generate-en-gb-dictionary.js
 * See docs/dictionaries/enGBWordlistReadme.txt for provenance.
 */

const { readFileSync, writeFileSync } = require('fs');
const { join } = require('path');
const { print, printError } = require('./_printers.js');

const DELIMITER = '\t';
const MAX_WORD_FREQUENCY = 9999; // mirrors ext.MAX_WORD_FREQUENCY in app/constants.gradle

const REPO_ROOT = join(__dirname, '..');
const VARCON_FILE = join(__dirname, 'varcon', 'varcon.txt');
const SOURCE_CSV = join(REPO_ROOT, 'app', 'languages', 'dictionaries', 'en-utf8.csv');
const OUTPUT_CSV = join(REPO_ROOT, 'app', 'languages', 'dictionaries', 'en-GB-utf8.csv');


/**
 * Parse VarCon into a map: britishWord -> Set(americanWord).
 *
 * VarCon groups every spelling of one word on a single cluster line, e.g.
 *   A Cv: theater / B C: theatre
 * Each entry is "TAGS: word"; entries are separated by " / "; anything after
 * "|" is a note. The category is the first letter of each tag token:
 *   A = American, B = British (traditional -ise), Z = British with -ize
 *   spelling, C = Canadian, D = Australian, _ = common.
 * A tag with a trailing variant marker (Av, BV, C1, ...) is a variant, not the
 * primary form. We pair the PRIMARY American form (token exactly "A") with the
 * PRIMARY British form (token exactly "B"). Z is deliberately ignored: it is the
 * -ize spelling, which is what we are steering away from.
 */
function parseVarcon(text) {
	const britishToAmerican = new Map();
	let clusterCount = 0;

	for (const rawLine of text.split('\n')) {
		const line = rawLine.replace(/\|.*$/, '').trim(); // drop notes after "|"
		if (line === '' || line.startsWith('#')) {
			continue;
		}

		let american = null;
		let british = null;
		for (const entry of line.split(' / ')) {
			const sep = entry.indexOf(': ');
			if (sep === -1) {
				continue;
			}
			const tags = entry.slice(0, sep).trim().split(/\s+/);
			const word = entry.slice(sep + 2).trim();
			if (word === '') {
				continue;
			}
			if (american === null && tags.includes('A')) {
				american = word;
			}
			if (british === null && tags.includes('B')) {
				british = word;
			}
		}

		if (american === null || british === null || american === british) {
			continue;
		}

		clusterCount++;
		if (!britishToAmerican.has(british)) {
			britishToAmerican.set(british, new Set());
		}
		britishToAmerican.get(british).add(american);
	}

	return { britishToAmerican, clusterCount };
}


/** Split a dictionary line into [word, frequency]. Missing frequency is 0. */
function parseLine(line) {
	const tab = line.indexOf(DELIMITER);
	if (tab === -1) {
		return [line, 0];
	}
	const freq = Number.parseInt(line.slice(tab + 1), 10);
	return [line.slice(0, tab), Number.isNaN(freq) ? 0 : freq];
}


function generate() {
	const varconText = readFileSync(VARCON_FILE, 'utf8');
	const { britishToAmerican, clusterCount } = parseVarcon(varconText);

	// Preserve exact bytes: split on '\n'; a trailing '\n' yields a final ''
	// element, so join('\n') reproduces the file verbatim.
	const source = readFileSync(SOURCE_CSV, 'utf8');
	const inputLines = source.split('\n');

	const wordToIndex = new Map();
	const originalFreq = new Map();
	inputLines.forEach((line, i) => {
		if (line === '') {
			return; // trailing element / blank lines
		}
		const [word, freq] = parseLine(line);
		wordToIndex.set(word, i);
		originalFreq.set(word, freq);
	});

	// An applicable pair is one where the British spelling and at least one of
	// its American counterparts both exist in en-utf8.csv. Keep only present
	// American forms.
	const applicable = new Map(); // british -> [present americans]
	for (const british of [...britishToAmerican.keys()].sort()) {
		if (!wordToIndex.has(british)) {
			continue;
		}
		const americans = [...britishToAmerican.get(british)].sort().filter((a) => wordToIndex.has(a));
		if (americans.length > 0) {
			applicable.set(british, americans);
		}
	}

	// Resolve final frequencies as a DAG: a British form must sit just above the
	// FINAL frequency of its American pair, and that American may itself be a
	// British form re-ranked elsewhere (e.g. the jibe/gybe/gibe homograph chain).
	// Memoize; detect and reject any dependency cycle rather than looping forever.
	const finalFreq = new Map();
	const inProgress = new Set();

	const resolve = (word) => {
		if (finalFreq.has(word)) {
			return finalFreq.get(word);
		}
		if (!applicable.has(word)) {
			return originalFreq.get(word); // pure American form: never re-ranked
		}
		if (inProgress.has(word)) {
			printError(`Verification failed: dependency cycle involving "${word}" — cannot re-rank consistently.`);
			process.exit(4);
		}

		inProgress.add(word);
		let referenceFreq = -1;
		for (const american of applicable.get(word)) {
			referenceFreq = Math.max(referenceFreq, resolve(american));
		}
		const value = Math.max(originalFreq.get(word), referenceFreq) + 1; // minimal-edge: just above US
		if (value > MAX_WORD_FREQUENCY) {
			printError(`Refusing to exceed MAX_WORD_FREQUENCY (${MAX_WORD_FREQUENCY}) for "${word}".`);
			process.exit(3);
		}
		inProgress.delete(word);
		finalFreq.set(word, value);
		return value;
	};

	const outputLines = inputLines.slice();
	const changedIndices = new Set();
	for (const british of applicable.keys()) {
		const value = resolve(british);
		outputLines[wordToIndex.get(british)] = `${british}${DELIMITER}${value}`;
		changedIndices.add(wordToIndex.get(british));
	}

	verify(inputLines, outputLines, changedIndices, applicable, wordToIndex);

	writeFileSync(OUTPUT_CSV, outputLines.join('\n'));

	print(`VarCon clusters with an A<->B pair: ${clusterCount}`);
	print(`British variants re-ranked (both forms present): ${applicable.size}`);
	print(`Words dropped: 0   Words added: 0`);
	print(`Wrote ${OUTPUT_CSV}`);
}


/**
 * Fail loudly if any invariant breaks. Correctness is enforced here, at the
 * point where the data is produced.
 */
function verify(inputLines, outputLines, changedIndices, applicable, wordToIndex) {
	const fail = (msg) => { printError(`Verification failed: ${msg}`); process.exit(4); };

	if (inputLines.length !== outputLines.length) {
		fail(`line count changed (${inputLines.length} -> ${outputLines.length}).`);
	}

	// 1. Every non-variant line is byte-identical to en-utf8.csv.
	for (let i = 0; i < inputLines.length; i++) {
		if (!changedIndices.has(i) && inputLines[i] !== outputLines[i]) {
			fail(`unexpected change on line ${i + 1}.`);
		}
	}

	// 2. Zero words dropped or added: same word on every line, same order.
	for (let i = 0; i < inputLines.length; i++) {
		if (inputLines[i] === '') {
			continue;
		}
		const before = parseLine(inputLines[i])[0];
		const after = parseLine(outputLines[i])[0];
		if (before !== after) {
			fail(`word changed on line ${i + 1} ("${before}" -> "${after}").`);
		}
	}

	// 3/4. Every re-ranked British variant now outranks each of its American
	//      pairs, and every American form is still present as a (lower-ranked)
	//      fallback.
	const freqOf = (word) => parseLine(outputLines[wordToIndex.get(word)])[1];
	for (const [british, americans] of applicable) {
		for (const american of americans) {
			if (!wordToIndex.has(american)) {
				fail(`American fallback "${american}" is missing.`);
			}
			if (freqOf(british) <= freqOf(american)) {
				fail(`"${british}" (${freqOf(british)}) does not outrank "${american}" (${freqOf(american)}).`);
			}
		}
	}
}


try {
	generate();
} catch (e) {
	printError(e);
	process.exit(1);
}
