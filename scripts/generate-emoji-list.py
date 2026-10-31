#!/usr/bin/env python3
"""
Builds app/src/main/res/raw/emoji_list.txt from Unicode's emoji-test.txt.

    python3 -I scripts/generate-emoji-list.py /path/to/emoji-test.txt

Get emoji-test.txt from https://unicode.org/Public/emoji/<version>/emoji-test.txt. The output keeps
the official order and groups, fully-qualified entries only. It leaves out the "Component" group and
every skin-tone variant, so each person emoji appears once; the phone's font decides at runtime
which of the rest it can draw (see EmojiData.java).

Output format, one entry per line:
    =<icon><TAB><group name>     starts a group ("=" because no emoji starts with it; "#" would clash
                                 with the keycap #️⃣)
    <emoji>                      an entry of the current group
"""
import sys

GROUP_ICONS = {
    "Smileys & Emotion": "😀",
    "People & Body": "👍",
    "Animals & Nature": "🐻",
    "Food & Drink": "🍔",
    "Travel & Places": "🚗",
    "Activities": "⚽",
    "Objects": "💡",
    "Symbols": "❤️",
    "Flags": "🏁",
}
SKIP_GROUPS = {"Component"}
SKIN_TONES = {0x1F3FB, 0x1F3FC, 0x1F3FD, 0x1F3FE, 0x1F3FF}


def main(source, target):
    out = []
    group = None
    count = 0
    with open(source, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if line.startswith("# group: "):
                group = line[len("# group: "):].strip()
                if group not in SKIP_GROUPS:
                    out.append("=" + GROUP_ICONS.get(group, "•") + "\t" + group)
                continue
            if not line or line.startswith("#") or group in SKIP_GROUPS:
                continue
            codes, _, status_and_rest = line.partition(";")
            if not status_and_rest.strip().startswith("fully-qualified"):
                continue
            code_points = [int(c, 16) for c in codes.split()]
            if any(cp in SKIN_TONES for cp in code_points):
                continue
            out.append("".join(chr(cp) for cp in code_points))
            count += 1
    with open(target, "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")
    print(f"{count} emojis in {len(GROUP_ICONS)} groups -> {target}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1], "app/src/main/res/raw/emoji_list.txt")
