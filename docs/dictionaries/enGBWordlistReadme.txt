English (UK) [beta] wordlist — en-GB-utf8.csv

This dictionary is NOT an independent word list. It is a razor-thin re-ranking
of the generic English list (en-utf8.csv, see enWordlistReadme.txt for that
list's full provenance). Every British spelling variant is bumped just above its
American counterpart; every other line is byte-identical to en-utf8.csv. No
words are added, removed, or rewritten — American spellings remain as
lower-ranked fallbacks.

=====

Generation (deterministic, reproducible byte-for-byte)

  Generator: scripts/generate-en-gb-dictionary.js  (dev-time tool; NOT wired
             into the Gradle build)
  Inputs:    app/languages/dictionaries/en-utf8.csv  (the English source list)
             scripts/varcon/varcon.txt               (vendored VarCon, below)
  Output:    app/languages/dictionaries/en-GB-utf8.csv

  Run:       node scripts/generate-en-gb-dictionary.js

  For each American<->British pair where BOTH spellings already exist in
  en-utf8.csv, the generator sets:
      British frequency = max(British, American) + 1
  leaving the American frequency untouched. The generator self-verifies and
  fails loudly if any invariant breaks (every re-ranked British form outranks
  its American pair; every American form is still present; every non-variant
  line is byte-identical; zero words dropped or added).

  en-GB-utf8.csv sha256:
    1eb7e82d19df958eb9c2f3a260dab570813be390405006b24e26d94a654201b1

=====

Pairing source: VarCon (Variant Conversion Info)

VarCon supplies the American<->British pairing only; the spellings themselves
already live in en-utf8.csv. British-preferred forms are the primary "B"
(traditional -ise) entries; the "Z" (British -ize) category is deliberately
ignored, as it is the spelling this dictionary steers away from.

  Source:   Kevin Atkinson's word list / SCOWL companion dataset
            https://github.com/en-wl/wordlist  (varcon/varcon.txt)
  Retrieved: 2026-07-14 (from the master branch)
  Vendored:  scripts/varcon/varcon.txt
  sha256:    75af63da46ec12d7eb14b9f1ba8d3898d484dd6872755b73c921b215875a3629
  git blob:  973e351302fb2943692f82d9e0f1bb63894552f9

The vendored file's checksum above is the version pin: re-running the generator
against these exact bytes reproduces en-GB-utf8.csv byte-for-byte.

VarCon is under the following copyright:

  Copyright 2000-2016 by Kevin Atkinson

  Permission to use, copy, modify, distribute and sell this array, the
  associated software, and its documentation for any purpose is hereby
  granted without fee, provided that the above copyright notice appears
  in all copies and that both that copyright notice and this permission
  notice appear in supporting documentation. Kevin Atkinson makes no
  representations about the suitability of this array for any
  purpose. It is provided "as is" without express or implied warranty.

  Copyright 2016 by Benjamin Titze

  Permission to use, copy, modify, distribute and sell this array, the
  associated software, and its documentation for any purpose is hereby
  granted without fee, provided that the above copyright notice appears
  in all copies and that both that copyright notice and this permission
  notice appear in supporting documentation. Benjamin Titze makes no
  representations about the suitability of this array for any
  purpose. It is provided "as is" without express or implied warranty.

=====

N-grams for next-word prediction / autocompletion are shared with the generic
English language (en-ngrams.zip); en-GB does not ship its own ngram asset.
