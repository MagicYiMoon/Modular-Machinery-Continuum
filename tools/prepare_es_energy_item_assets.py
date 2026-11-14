"""Adapt NECO's five charge-state item meshes to 1.20.1 item overrides.
GPL-3.0-only; source: EStorageEnergyCellItemRenderer, NECO b37fbb53.
Uses already-adapted upstream models and leaves their PNG files unchanged.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/modularmachinery'
DISPLAY = {'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]}}
for tier, letter in ((4, 'a'), (6, 'b'), (9, 'c')):
    name = f'estorage_energy_cell_l{tier}'
    prefix = f'modularmachinery:block/neco/block/estorage_energy_cell/storage_array_capacitance_{letter}/storage_array_capacitance_{letter}_'
    data = {'parent': prefix + 'empty', 'display': DISPLAY, 'overrides': []}
    for value, status in enumerate(('low', 'mid', 'high', 'full'), 1):
        variant = f'{name}_{status}'
        (ROOT / f'models/item/{variant}.json').write_text(json.dumps({'parent': prefix + status, 'display': DISPLAY}, indent=2) + '\n', encoding='utf-8')
        data['overrides'].append({'predicate': {'modularmachinery:estorage_charge': value}, 'model': f'modularmachinery:item/{variant}'})
        model_path = ROOT / ('models/' + (prefix + status).split(':', 1)[1] + '.json')
        model = json.loads(model_path.read_text(encoding='utf-8'))
        # Same full cube and full-bright overlay as source; separate coincident solid faces.
        for element in model['elements']:
            if element.get('name') != 'bloom':
                element['from'] = [0.01, 0.01, 0.01]
                element['to'] = [15.99, 15.99, 15.99]
        model_path.write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
    (ROOT / f'models/item/{name}.json').write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
print('Adapted three ES item models, four charge overrides each, GUI front orientation and inset solid shells; no textures changed.')
