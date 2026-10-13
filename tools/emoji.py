"""Builds Color Emoji’s color emoji from Apple's emoji font.

Usage: python3 tools/emoji.py AppleColorEmoji-Linux.ttf emoji-test.txt src/client/resources/color-emoji/emoji

The font comes from https://github.com/samuelngs/apple-emoji-ttf/releases (AppleColorEmoji-Linux.ttf) and the
emoji list from https://unicode.org/Public/emoji/latest/emoji-test.txt. Needs fonttools, uharfbuzz, Pillow, and
imagequant (pip install fonttools uharfbuzz pillow imagequant).

Writes one PNG per emoji, named by its code points, and emoji.txt: one line per PNG listing every sequence that
shows it, the PNG's own name first. Each PNG is Apple's own 40 px drawing, cut to 256 colors to keep the JAR small.
"""
import io
import sys
from pathlib import Path

import imagequant
import uharfbuzz as hb
from fontTools.ttLib import TTFont
from PIL import Image

STRIKE_PPEM = 40
STATUSES = {"fully-qualified", "minimally-qualified", "unqualified", "component"}


def sequences(path):
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        points, status = (part.strip() for part in line.split(";"))
        if status in STATUSES:
            yield [int(point, 16) for point in points.split()], status


def main(font_path, test_path, out_dir):
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.png"):
        old.unlink()
    data = Path(font_path).read_bytes()
    font = TTFont(io.BytesIO(data))
    strike = next(i for i, s in enumerate(font["CBLC"].strikes) if s.bitmapSizeTable.ppemY == STRIKE_PPEM)
    bitmaps = font["CBDT"].strikeData[strike]
    order = font.getGlyphOrder()
    shaper = hb.Font(hb.Face(data))

    glyphs = {}  # glyph name -> sequences that show it, best first
    for points, status in sequences(test_path):
        # A text-style character such as U+2764 is only an emoji with U+FE0F after it.
        if len(points) == 1 and status == "unqualified":
            continue
        buffer = hb.Buffer()
        buffer.add_codepoints(points)
        buffer.guess_segment_properties()
        buffer.flags = hb.BufferFlags.REMOVE_DEFAULT_IGNORABLES
        hb.shape(shaper, buffer)
        if len(buffer.glyph_infos) != 1 or buffer.glyph_infos[0].codepoint == 0:
            continue
        name = order[buffer.glyph_infos[0].codepoint]
        if name in bitmaps and points not in glyphs.setdefault(name, []):
            glyphs[name].append(points)

    lines = []
    for name, shown in glyphs.items():
        key = "-".join(f"{point:x}" for point in shown[0])
        image = Image.open(io.BytesIO(bitmaps[name].imageData)).convert("RGBA")
        image = imagequant.quantize_pil_image(image, max_colors=256)
        image.save(out / f"{key}.png", optimize=True)
        lines.append(" ".join("-".join(f"{point:x}" for point in points) for points in shown))
    (out / "emoji.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{len(lines)} emoji, {sum(len(line.split()) for line in lines)} sequences")


if __name__ == "__main__":
    main(*sys.argv[1:])
