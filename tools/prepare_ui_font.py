"""Reproduce MMC UI Sans from the pinned OFL Noto Sans SC variable font.

Development dependency: fonttools==4.65.0. Does not download files or install packages.
Usage: python tools/prepare_ui_font.py --source path/to/NotoSansSC-VF.ttf
"""
# SPDX-License-Identifier: GPL-3.0-only
import argparse
import hashlib
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

SOURCE_SHA256 = "a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1]
                        / "src/main/resources/assets/modularmachinery/font/mmc_ui_sans.ttf")
    args = parser.parse_args()
    if hashlib.sha256(args.source.read_bytes()).hexdigest() != SOURCE_SHA256:
        raise SystemExit("Source hash differs; review the upstream font/version before updating it.")
    font = instantiateVariableFont(TTFont(args.source, recalcTimestamp=False), {"wght": 400}, inplace=True)
    options = subset.Options()
    options.name_IDs = ["*"]
    options.name_legacy = True
    options.name_languages = ["*"]
    options.recalc_timestamp = False
    sub = subset.Subsetter(options=options)
    sub.populate(unicodes=range(0x10000))
    sub.subset(font)
    # Minecraft 1.20.1 adds hmtx LSB to the outline bitmap bearing a second time.
    # Keep advances/outlines, neutralize the redundant LSB (notably fullwidth parentheses).
    for glyph, (advance, _) in font["hmtx"].metrics.items():
        font["hmtx"].metrics[glyph] = (advance, 0)
    font["head"].flags &= ~2
    names = {1: "MMC UI Sans", 4: "MMC UI Sans Regular", 6: "MMCUI-Sans-Regular",
             16: "MMC UI Sans", 17: "Regular"}
    for record in font["name"].names:
        if record.nameID in names:
            record.string = names[record.nameID].encode(record.getEncoding())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    font.save(args.output)
    print(f"Wrote {args.output}: {len(font.getBestCmap())} BMP code points, OFL-1.1")


if __name__ == "__main__":
    main()
