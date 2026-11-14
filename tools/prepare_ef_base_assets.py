"""GPL-3.0-only. Adapt pinned NECO EF offline/formed/worker artwork; KasumiNova and contributors.

No source PNG is resized or recolored. Run from any directory with Python 3.
"""
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'build/reference/nova-core-audit/source/src/main/resources/assets/novaeng_core'
DEST = ROOT / 'src/main/resources/assets/modularmachinery'
models, textures = {}, set()
BLOCKS = ['efabricator_casing', 'efabricator_vent', 'efabricator_worker', 'efabricator_pattern_bus', 'efabricator_me_channel']
for tier in (4, 6, 9):
    BLOCKS += [f'extendable_fabricator_subsystem_l{tier}', f'efabricator_parallel_proc_l{tier}']

def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def texture(ref):
    if not ref.startswith('novaeng_core:'):
        return ref
    name = ref.split(':', 1)[1]
    source = SOURCE / 'textures' / (name + '.png')
    target = DEST / 'textures/neco/efabricator' / (name + '.png')
    if name not in textures:
        textures.add(name)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        assert hashlib.sha256(source.read_bytes()).digest() == hashlib.sha256(target.read_bytes()).digest()
        if source.with_suffix('.png.mcmeta').exists():
            shutil.copyfile(source.with_suffix('.png.mcmeta'), target.with_suffix('.png.mcmeta'))
    return 'modularmachinery:neco/efabricator/' + name

def model(ref):
    if not ref.startswith('novaeng_core:'):
        return ref
    name = ref.split(':', 1)[1]
    if name not in models:
        data = read(SOURCE / 'models/block' / (name + '.json'))
        models[name] = data
        data['parent'] = model(data['parent']) if 'parent' in data else 'minecraft:block/block'
        data['textures'] = {key: texture(value) for key, value in data.get('textures', {}).items()}
        data['render_type'] = 'minecraft:cutout'
        def resolve(ref):
            while ref.startswith('#'):
                ref = data['textures'][ref[1:]]
            return ref
        elements = data.get('elements', [])
        glow = [element for element in elements if 'bloom' in element.get('name', '').lower()
                or element.get('faces') and all('/bloom/' in resolve(face['texture']) for face in element['faces'].values())]
        for element in glow:
            # Retain the original overlay silhouette/UVs. Separate coincident solid shells
            # for the single 1.20 cutout pass, as in the EC/ES adaptation.
            for body in elements:
                if body not in glow and body.get('from') == element.get('from') and body.get('to') == element.get('to') and body.get('rotation') == element.get('rotation'):
                    if all(b > a + .02 for a, b in zip(body['from'], body['to'])):
                        body['from'] = [v + .01 for v in body['from']]
                        body['to'] = [v - .01 for v in body['to']]
            element['forge_data'] = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
        for element in data.get('elements', []):
            for face in element.get('faces', {}).values():
                ref = face.get('texture', '')
                seen = set()
                while ref.startswith('#'):
                    assert ref not in seen, (name, 'texture cycle', ref)
                    seen.add(ref)
                    assert ref[1:] in data['textures'], (name, 'undefined face', ref)
                    ref = data['textures'][ref[1:]]
        write(DEST / 'models/block/neco/efabricator' / (name + '.json'), data)
    return 'modularmachinery:block/neco/efabricator/' + name

for block in BLOCKS:
    source = read(SOURCE / 'blockstates' / (block + '.json'))
    if block == 'efabricator_worker':
        entries = []
        for entry in source['multipart']:
            when = dict(entry['when'])
            apply = dict(entry['apply']); apply['model'] = model(apply['model'])
            entries.append({'when': when, 'apply': apply})
        data = {'multipart': entries}
        parent = model(source['multipart'][0]['apply']['model'])
    elif block.startswith('extendable_fabricator'):
        variants = {}
        for key, entry in source['variants'].items():
            if key == 'inventory':
                continue
            entry = dict(entry); entry['model'] = model(entry['model'])
            variants[key] = entry
        data = {'variants': variants}; parent = variants['facing=north,formed=false']['model']
    elif block == 'efabricator_casing':
        parent = model(source['defaults']['model']); data = {'variants': {'': {'model': parent}}}
    else:
        variants = {}
        for key, entry in source['variants'].items():
            if key == 'inventory':
                continue
            entry = dict(entry); entry['model'] = model(entry['model']); variants[key] = entry
        data = {'variants': variants}; parent = variants['facing=north']['model']
    write(DEST / 'blockstates' / (block + '.json'), data)
    write(DEST / 'models/item' / (block + '.json'), {'parent': parent})
    entry = {'type': 'minecraft:item', 'name': 'modularmachinery:' + block}
    for field, target in [('WorkData', 'EfWorkData')] if block == 'efabricator_worker' else [('PatternData', 'EfPatternData')] if block == 'efabricator_pattern_bus' else [('EfProduction', 'EfProduction')] if block.startswith('extendable_fabricator') else []:
        entry['functions'] = [{'function': 'minecraft:copy_nbt', 'source': 'block_entity',
                               'ops': [{'source': field, 'target': target, 'op': 'replace'}]}]
    write(ROOT / 'src/main/resources/data/modularmachinery/loot_tables/blocks' / (block + '.json'), {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [entry], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})

# 1.20.1 only stitches explicitly discovered atlas directories. Model references alone
# do not include our separate legacy-artwork directory in the block atlas.
atlas_path = ROOT / 'src/main/resources/assets/minecraft/atlases/blocks.json'
atlas = read(atlas_path)
atlas_source = {'type': 'minecraft:directory', 'source': 'neco/efabricator', 'prefix': 'neco/efabricator/'}
if atlas_source not in atlas['sources']:
    atlas['sources'].append(atlas_source)
write(atlas_path, atlas)

for tag in ('mineable/pickaxe', 'needs_iron_tool'):
    path = ROOT / 'src/main/resources/data/minecraft/tags/blocks' / (tag + '.json')
    data = read(path)
    for block in BLOCKS:
        if 'modularmachinery:' + block not in data['values']:
            data['values'].append('modularmachinery:' + block)
    write(path, data)

for locale in ('zh_cn', 'en_us'):
    path = DEST / 'lang' / (locale + '.json'); data = read(path)
    english = locale == 'en_us'
    names = {'efabricator_casing': ('合成系统结构外壳', 'EF Structural Casing'),
             'efabricator_vent': ('合成系统结构散热器', 'EF Structural Vent'),
             'efabricator_worker': ('FX 工作核心', 'FX Fabrication Worker'),
             'efabricator_pattern_bus': ('FD 智能样板总线', 'FD Pattern Bus'),
             'efabricator_me_channel': ('FR ME 矩阵通讯接口', 'FR ME Channel')}
    for tier in (4, 6, 9):
        names[f'extendable_fabricator_subsystem_l{tier}'] = (f'F{tier} 可扩展合成子系统主机', f'F{tier} Extendable Fabricator Subsystem')
        names[f'efabricator_parallel_proc_l{tier}'] = (f'FT{tier} 并行核心', f'FT{tier} Parallel Processor')
    for block, name in names.items():
        data['block.modularmachinery.' + block] = name[english]
    texts = {'tooltip.ef.development': ('EF 开发阶段：已接入 ME 制造、主动冷却、样板界面、运行外观与 TOP 工作核心信息。', 'EF development stage: ME fabrication, active cooling, pattern menus, status artwork and TOP worker information.'),
             'tooltip.ef.worker': ('默认队列：%s 次合成；能量缓存：%s AE。', 'Base queue: %s crafts; energy cache: %s AE.'),
             'tooltip.ef.patterns': ('样板库存：%s 格，每格一枚编码样板。', 'Pattern inventory: %s slots, one encoded pattern per slot.'),
             'tooltip.ef.parallel': ('基础并行额度：%s。', 'Base parallel allowance: %s.'),
             'message.ef.formed': ('EF 结构已成型。', 'EF structure assembled.'),
             'message.ef.unformed': ('EF 尚未成型，请检查对应的机器定义与结构。', 'EF is not assembled; check the matching machine definition and structure.')}
    for key, text in texts.items():
        data[key] = text[english]
    write(path, data)

print(f'EF base assets PASS: {len(BLOCKS)} blocks, {len(models)} models, {len(textures)} unchanged PNGs; face references resolved, block atlas directory registered')
