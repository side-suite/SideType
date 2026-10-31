# Unicode emoji data

`app/src/main/res/raw/emoji_list.txt` is generated from Unicode's `emoji-test.txt`
(Unicode Emoji 16.0, dated 2024-08-14) by `scripts/generate-emoji-list.py`:

```bash
curl -O https://unicode.org/Public/emoji/16.0/emoji-test.txt
python3 -I scripts/generate-emoji-list.py emoji-test.txt
```

It keeps the official order and groups and every fully-qualified emoji, leaves out the
"Component" group and all skin-tone variants, and is filtered at runtime against what the
phone's font can draw (`EmojiData.java`).

The source data is © Unicode, Inc. and used under the Unicode License
(<https://www.unicode.org/license.txt>), which permits redistribution with this notice.
