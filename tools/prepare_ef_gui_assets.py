"""Copy NECO b37fbb53 EF GUI PNGs unchanged; derive mode-glyph colors (GPL-3.0-only)."""
from pathlib import Path
import hashlib
import shutil
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'build/reference/nova-core-audit/source/src/main/resources/assets/novaeng_core/textures/gui'
DEST = ROOT / 'src/main/resources/assets/modularmachinery/textures/gui/neco'
DEST.mkdir(parents=True, exist_ok=True)
for name in ('efabricator_dark', 'efabricator_elements', 'efabricator_inventory', 'efabricator_pattern_bus', 'efabricator_pattern_search_bg_dark', 'efabricator_search'):
    src, dest = SOURCE / (name + '.png'), DEST / (name + '.png')
    shutil.copyfile(src, dest)
    assert hashlib.sha256(src.read_bytes()).digest() == hashlib.sha256(dest.read_bytes()).digest()
print('EF GUI: 6 original PNGs, byte-for-byte; no resampling or recoloring')

# Keep the upstream sheet unchanged. Only the mode glyphs in this derived sheet
# are recolored; translucent backgrounds, borders and pressed-state shades stay.
original = Image.open(DEST / 'efabricator_elements.png').convert('RGBA')
modes = original.copy()
changed = 0
for u in (1, 38, 75, 112):
    for v, color in ((1, (64, 167, 255)), (38, (242, 164, 82))):
        opaque = {(x, y) for x in range(36) for y in range(36)
                  if original.getpixel((u + x, v + y))[3] == 255}
        glyph = set()
        while opaque:
            start = opaque.pop()
            component, pending = {start}, [start]
            while pending:
                x, y = pending.pop()
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        point = (x + dx, y + dy)
                        if point in opaque:
                            opaque.remove(point)
                            component.add(point)
                            pending.append(point)
            # Frame components reach the rim; icon strokes and sparks do not.
            if all(3 <= x < 33 and 3 <= y < 33 for x, y in component):
                glyph.update(component)
        assert len(glyph) >= 100, (u, v, len(glyph))
        for x, y in glyph:
            r, _, _, alpha = original.getpixel((u + x, v + y))
            modes.putpixel((u + x, v + y), tuple(round(c * r / 240) for c in color) + (alpha,))
        changed += len(glyph)
assert modes.getchannel('A').tobytes() == original.getchannel('A').tobytes()
modes.save(DEST / 'efabricator_modes.png')
print(f'EF modes: {changed} glyph pixels recolored, blue enabled / orange disabled; original alpha and frames retained')
