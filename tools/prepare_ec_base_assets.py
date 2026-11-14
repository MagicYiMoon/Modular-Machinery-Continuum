"""Adapt EC offline/formed/online artwork from pinned, locally cached NECO sources.

GPL-3.0-only. Original artwork: KasumiNova / NovaEngineering Core contributors.
World cosmetic states are selected via bounded block-entity ModelData.
"""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "build/reference/nova-core-audit/source/src/main/resources/assets/novaeng_core"
DEST = ROOT / "src/main/resources/assets/modularmachinery"
seen_models, seen_textures = set(), set()


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def texture(ref):
    if not ref.startswith("novaeng_core:"):
        return ref
    name = ref.split(":", 1)[1]
    if name not in seen_textures:
        seen_textures.add(name)
        source = SOURCE / "textures" / (name + ".png")
        target = DEST / "textures/neco" / (name + ".png")
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        if source.with_suffix(".png.mcmeta").exists():
            shutil.copyfile(source.with_suffix(".png.mcmeta"), target.with_suffix(".png.mcmeta"))
    return "modularmachinery:neco/" + name


def model(ref, item=False):
    if not ref.startswith("novaeng_core:"):
        return ref
    name = ref.split(":", 1)[1]
    key = (name, item)
    destination = ("item/" if item else "block/neco/") + name
    if key in seen_models:
        return "modularmachinery:" + destination
    seen_models.add(key)
    data = read(SOURCE / "models" / ("item" if item else "block") / (name + ".json"))
    if "parent" in data:
        data["parent"] = model(data["parent"])
    elif not item:
        data["parent"] = "minecraft:block/block"
    data["textures"] = {k: texture(v) for k, v in data.get("textures", {}).items()}
    data["render_type"] = "minecraft:cutout"
    # Upstream L6/L9 element 22 has six #missing faces. The same L4 element
    # uses its tier's hyper-thread panel, with identical geometry/UVs.
    # Bind only those broken references to the corresponding existing tier panel.
    repairs = {f"ec_modular_synthetic_memory/thread_module/{phase}/l{tier}_hyper_threading_module": panel
               for phase in ("off", "on", "run") for tier, panel in ((6, "#72"), (9, "#6"))}
    if name in repairs:
        for element in data.get("elements", []):
            for face in element.get("faces", {}).values():
                if face.get("texture") == "#missing":
                    face["texture"] = repairs[name]
    if name == "ec_modular_synthetic_memory/tail_management_bus/l4_tail_management_bus_off":
        # L4 omits particle; L6's equivalent model explicitly uses this housing texture.
        data["textures"]["particle"] = "#65"
    def texture_value(ref):
        while ref.startswith("#"):
            ref = data["textures"][ref[1:]]
        return ref

    # Most source EC glow elements are unnamed. Detect their actual sprite, too.
    elements = data.get("elements", [])
    glow = [element for element in elements if "bloom" in element.get("name", "").lower()
            or element.get("faces") and all("/bloom/" in texture_value(face["texture"])
                                            for face in element["faces"].values())]
    for element in glow:
        # Source bloom used a separate pass. Separate coincident shells for a single cutout pass,
        # as in the ES adaptation, while retaining the source overlay's outer silhouette/UVs.
        for body in elements:
            if body not in glow and body.get("from") == element.get("from") and body.get("to") == element.get("to") and body.get("rotation") == element.get("rotation"):
                if all(b > a + .02 for a, b in zip(body["from"], body["to"])):
                    body["from"] = [value + .01 for value in body["from"]]
                    body["to"] = [value - .01 for value in body["to"]]
        if element:
            element["forge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
    write(DEST / "models" / (destination + ".json"), data)
    return "modularmachinery:" + destination


block_ids = ["ecalculator_casing", "ecalculator_cell_drive", "ecalculator_me_channel", "ecalculator_transmitter_bus"]
for tier in (4, 6, 9):
    block_ids += [f"extendable_calculator_subsystem_l{tier}", f"ecalculator_thread_core_l{tier}",
                  f"ecalculator_thread_core_hyper_l{tier}", f"ecalculator_parallel_proc_l{tier}", f"ecalculator_tail_l{tier}"]

for block in block_ids:
    source = read(SOURCE / "blockstates" / (block + ".json"))
    if block == "ecalculator_cell_drive":
        entries = []
        for entry in source["multipart"]:
            when = dict(entry["when"])
            when["memory"] = "|".join(str({"empty": 0, "a": 1, "b": 2, "c": 3}[grade]) for grade in when.pop("storage_level").split("|"))
            if "status" in when:
                when["online"] = "true" if when.pop("status") == "on" else "false"
            apply = dict(entry["apply"]); apply["model"] = model(apply["model"])
            entries.append({"when": when, "apply": apply})
        write(DEST / "blockstates" / (block + ".json"), {"multipart": entries})
        parent = model(source["multipart"][0]["apply"]["model"])
    else:
        if "variants" in source and "facing=north,status=off" in source["variants"]:
            ref = source["variants"]["facing=north,status=off"]["model"]
        elif "variants" in source and "facing=north,formed=false" in source["variants"]:
            ref = source["variants"]["facing=north,formed=false"]["model"]
        elif "multipart" in source:
            ref = source["multipart"][0]["apply"]["model"]
        else:
            ref = source["defaults"]["model"]
        parent = model(ref)
        if block in ("ecalculator_casing", "ecalculator_me_channel"):
            variants = {"": {"model": parent}}
        else:
            variants = {f"facing={facing}": {"model": parent, **({"y": rotation} if rotation else {})}
                        for facing, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}
        if block == "ecalculator_transmitter_bus":
            entries = []
            for entry in source["multipart"]:
                apply = dict(entry["apply"]); apply["model"] = model(apply["model"])
                when = dict(entry["when"])
                # Upstream omits NONE only for south/up; make this unreachable fallback
                # consistent with the other three facings (formed links always have a tier).
                if when == {"facing": "south", "link": "up", "link_level": "l4"}:
                    when["link_level"] = "none|l4"
                entries.append({"when": when, "apply": apply})
            state_data = {"multipart": entries}
        elif block not in ("ecalculator_casing", "ecalculator_me_channel"):
            variants = {}
            for key, value in source["variants"].items():
                if key == "inventory": continue
                if "parallel_proc" in block:
                    key = key.replace("status=off", "online=false").replace("status=on", "online=true")
                entry = dict(value); entry["model"] = model(entry["model"])
                variants[key] = entry
            state_data = {"variants": variants}
        else:
            state_data = {"variants": variants}
        write(DEST / "blockstates" / (block + ".json"), state_data)
    item_model = {"parent": parent}
    if block.startswith("ecalculator_tail_"):
        # Three-block-tall source geometry must fit one inventory/JEI icon.
        # This changes only the GUI transform, never world geometry.
        item_model["display"] = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]}}
    write(DEST / "models/item" / (block + ".json"), item_model)
    core = "thread_core" in block
    loot = {"type": "minecraft:block", "pools": [] if core else [{"rolls": 1,
            "entries": [{"type": "minecraft:item", "name": "modularmachinery:" + block}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]}
    write(ROOT / "src/main/resources/data/modularmachinery/loot_tables/blocks" / (block + ".json"), loot)

for capacity in (64, 1024, 16384):
    model(f"novaeng_core:ecalculator_cell_{capacity}m", item=True)

for tag in ("mineable/pickaxe", "needs_iron_tool"):
    path = ROOT / "src/main/resources/data/minecraft/tags/blocks" / (tag + ".json")
    data = read(path) if path.exists() else {"replace": False, "values": []}
    for block in block_ids:
        if "modularmachinery:" + block not in data["values"]:
            data["values"].append("modularmachinery:" + block)
    write(path, data)

print(f"EC source assets: {len(block_ids)} blocks, {len(seen_models)} models, {len(seen_textures)} textures")
