"""Reproduce EC monitor artwork from pinned NECO b37fbb53 sources (GPL-3.0-only).

Panels are copied byte-for-byte. Core thumbnails use the first 16x16 frame of
upstream on-state sprites, placed on a transparent 256x256 GUI sheet. No resampling.
"""
from pathlib import Path
import shutil
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'build/reference/nova-core-audit/source/src/main/resources/assets/novaeng_core/textures'
DEST = ROOT / 'src/main/resources/assets/modularmachinery/textures/gui/neco'
DEST.mkdir(parents=True, exist_ok=True)
for name in ('ecalculator_background', 'ecalculator_gui_1', 'ecalculator_gui_2'):
    shutil.copyfile(SOURCE / 'gui' / (name + '.png'), DEST / (name + '.png'))
for tier in (4, 6, 9):
    for hyper in (False, True):
        name = ('l' + str(tier) + '_' if tier != 4 else '') + ('hyper_threading_module' if hyper else 'thread_module')
        sprite = Image.open(SOURCE / 'blocks/ec_modular_synthetic_memory/bloom/thread_module/on' / (name + '_on.png')).convert('RGBA')
        canvas = Image.new('RGBA', (256, 256))
        canvas.paste(sprite.crop((0, 0, 16, 16)), (0, 0))
        canvas.save(DEST / ('ec_core_l' + str(tier) + ('_hyper' if hyper else '') + '.png'))
print('EC GUI: 3 original panels, 6 unscaled source thumbnails')
